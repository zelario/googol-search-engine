package projetosd;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Implementation of the GatewayInterface for clients.
 * Exposes RMI methods for adding URLs and searching.
 * 
 * @author Jose Amado & José Capinha
 * @version 1.0
 */
public class Gateway extends UnicastRemoteObject implements GatewayInterface {

    /**
     * Stats object to track various statistics.
     */
    private final Stats stats;

    /**
     * Reference to the URL queue.
     */
    private UrlQueueInterface queue;

    /**
     * Map of registered barrels by their port number.
     */
    private final Map<Integer, BarrelInterface> barrels;

    private final AtomicInteger lastBarrelIndex = new AtomicInteger(0);

    /**
     * Constructs the Gateway.
     * @throws RemoteException RMI exception
     */
    public Gateway() throws RemoteException {
        stats = new Stats();

        try {
            Registry registry = LocateRegistry.getRegistry(Config.URL_QUEUE_HOST, Config.URL_QUEUE_PORT);
            queue = (UrlQueueInterface) registry.lookup("queue");
            Log.info("[GATEWAY] Connected to URL Queue on " + Config.URL_QUEUE_HOST + ":" + Config.URL_QUEUE_PORT);
        } catch (NotBoundException | RemoteException e) {
            Log.error("[GATEWAY] URL Queue not available: " + e.getMessage());
            queue = null;
        }

        barrels = new ConcurrentHashMap<>();
    }

    //------------------------------------------- BARREL HANDLING METHODS --------------------------------------------------//

    /**
     * Select an available barrel entry (port + barrel instance) by pinging them.
     * @return Map entry of selected barrel port and instance, or null if none available
     */
    @SuppressWarnings({"ConstantConditions", "BusyWait"})
    private synchronized Map.Entry<Integer, BarrelInterface> selectBarrel() {
        List<Map.Entry<Integer, BarrelInterface>> entries = new ArrayList<>(barrels.entrySet());
        if (entries.isEmpty()) return null;

        int size = entries.size();
        int index = lastBarrelIndex.getAndUpdate(i -> (i + 1) % size);

        while (!entries.isEmpty()) {
            Map.Entry<Integer, BarrelInterface> entry = entries.get(index);
            int port = entry.getKey();
            BarrelInterface barrel = entry.getValue();

            boolean available = false;
            int attempts = 0;

            while (attempts < Config.GATEWAY_RETRIES) {
                try {
                    barrel.ping();
                    available = true;
                    break;
                } catch (RemoteException e) {
                    attempts++;
                    Log.warning("[GATEWAY] Ping failed for barrel " + port + " on attempt " + attempts + ": " + e.getMessage());
                    if (attempts >= Config.GATEWAY_RETRIES) break;
                    try {
                        Thread.sleep((long) (Config.GATEWAY_BACKOFF * Math.pow(2, attempts - 1)));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return null;
                    }
                }
            }

            if (available) {
                Log.info("[GATEWAY] Chosen barrel is fine. Selected barrel " + port);
                lastBarrelIndex.set((index + 1) % entries.size());
                return entry;
            } else {
                Log.error("[GATEWAY] Barrel " + port + " not available after retries. Removing from registry");
                barrels.remove(port);
                stats.removeBarrelStats(port);
                entries.remove(index);
                if (entries.isEmpty()) return null;
                if (index >= entries.size()) index = 0;
            }

            index = (index + 1) % entries.size();
        }
        return null;
    }

    /**
     * Method to get barrel all hashes to check consistency
     * @return Map with ports as keys and as values hash maps with tables as keys as the MD5 hashes as values
     */
    private Map<Integer, Map<String, String>> getBarrelHashes(Timestamp now){
        Map<Integer, Map<String, String>> barrelHashes = new HashMap<>();

        for(Integer barrelPort : Config.BARREL_PORTS) {
            try{
                BarrelInterface barrel = (BarrelInterface) LocateRegistry.getRegistry(Config.BARREL_HOSTS_TRANSLATION_TABLE.get(barrelPort), barrelPort).lookup("barrel");
                barrel.ping();

                barrelHashes.put(barrelPort, barrel.getMD5Hash("", now));
            }

            catch (NotBoundException | RemoteException ignored){
            }
        }

        return barrelHashes;
    }

    /**
     * Method to check all barrel hashes and compare to find mismatches
     * @param now Timestamp to get consistent hashes
     * @return HashMap with ports as keys and a list of table names where mismatches were found as value
     */
    private List<String> checkBarrelMismatches(Timestamp now){
        Set<String> mismatches = new HashSet<>();
        Map<Integer, Map<String, String>> allHashes = this.getBarrelHashes(now);

        // No more than 1 barrel, no sync needed
        if (allHashes.size() <= 1) return new ArrayList<>();

        // Get a reference db to check against the others
        int referencePort = allHashes.keySet().stream().findFirst().orElse(null);

        Map<String, String> referenceHashes = allHashes.get(referencePort);

        for (Map.Entry<Integer, Map<String, String>> barrelEntry : allHashes.entrySet()) {
            int barrelPort = barrelEntry.getKey();
            if (barrelPort == referencePort) continue;

            Map<String, String> otherHashes = barrelEntry.getValue();

            for (String table : referenceHashes.keySet()) {
                if (!referenceHashes.get(table).equals(otherHashes.get(table))) {
                    mismatches.add(table);
                }
            }

        }

        return new ArrayList<>(mismatches);
    }

    @SuppressWarnings({"BusyWait", "SleepWhileInLoop"})
    public boolean syncBarrels(int requesterPort) throws RemoteException {
        if(requesterPort != 0) Log.info("[GATEWAY] Barrel " + requesterPort + " requested synchronization");
        else Log.info("[GATEWAY] Starting period sync");

        Timestamp syncTime = Timestamp.valueOf(LocalDateTime.now());
        List<String> mismatches = checkBarrelMismatches(syncTime);

        if(mismatches.isEmpty()){
            Log.info("[GATEWAY] Found no mismatches in barrels");
            return true;
        }

        Log.warning("[GATEWAY] Barrel mismatch found");

        // Get tables row hashes to find missing info
        // barrelPort -> <row hash -> row content>
        Map<Integer, Map<String, Map<String, String>>> rowHashes = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for(Integer barrelPort : Config.BARREL_PORTS){
            futures.add(CompletableFuture.runAsync(() -> {
                for(int i = 0; i < Config.GATEWAY_RETRIES; i++){
                    try{
                        BarrelInterface barrel = (BarrelInterface) LocateRegistry.getRegistry(Config.BARREL_HOSTS_TRANSLATION_TABLE.get(barrelPort), barrelPort).lookup("barrel");
                        barrel.ping();
                        Map<String, Map<String, String>> barrelRows = new HashMap<>();

                        for (String table : mismatches) {
                            barrelRows.put(table, barrel.getMD5Hash(table, syncTime));
                        }

                        rowHashes.put(barrelPort, barrelRows);
                    } catch (NotBoundException | RemoteException e) {
                        if (i < Config.GATEWAY_RETRIES) {
                            try {
                                Log.warning("[GATEWAY] Failed connection attempt");
                                Thread.sleep((long) (Config.GATEWAY_BACKOFF * Math.pow(2, i - 1)));
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                        else Log.error("[GATEWAY] Failed to connect to barrel to sync:" + e.getMessage());
                    }
                }
            }));
        }

        // Wait for the async fetches
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        // Get all rows in all barrels
        // Table -> <row hash -> row content>
        Map<String, Map<String, String>> globalRows = new HashMap<>();
        for (Map<String, Map<String, String>> barrelData : rowHashes.values()) {
            for (Map.Entry<String, Map<String, String>> tableEntry : barrelData.entrySet()) {
                String table = tableEntry.getKey();
                globalRows.computeIfAbsent(table, k -> new HashMap<>())
                        .putAll(tableEntry.getValue());
            }
        }

        // Force order on insertion
        List<String> tableInsertOrder = List.of("stop_words", "words", "url", "words_url", "url_url");

        // finally introduce missing data into barrels by filtering which rows are not present in each barrel
        futures.clear();
        for (Integer barrelPort : rowHashes.keySet()) {
            Map<String, Map<String, String>> barrelData = rowHashes.get(barrelPort);
            futures.add(CompletableFuture.runAsync(() -> {
                for(int i = 0; i < Config.GATEWAY_RETRIES; i++){
                    try {
                        BarrelInterface barrel = (BarrelInterface) LocateRegistry.getRegistry(Config.BARREL_HOSTS_TRANSLATION_TABLE.get(barrelPort), barrelPort).lookup("barrel");
                        barrel.ping();

                        for (String table : tableInsertOrder) {
                            if(!barrelData.containsKey(table)) continue;

                            Map<String, String> barrelTable = barrelData.get(table);
                            Map<String, String> missingRows = globalRows.get(table).entrySet().stream()
                                    .filter(e -> !barrelTable.containsKey(e.getKey()))
                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                            if (!missingRows.isEmpty()) {
                                barrel.insertMissingRows(table, new ArrayList<>(missingRows.values()));
                            }
                        }

                    } catch (RemoteException | NotBoundException e) {
                        if (i < Config.GATEWAY_RETRIES) {
                            try {
                                Thread.sleep((long) (Config.GATEWAY_BACKOFF * Math.pow(2, i - 1)));
                            } catch (InterruptedException ie) {
                                Thread.currentThread().interrupt();
                                break;
                            }
                        }
                        else Log.error("[GATEWAY] Failed to connect to barrel to sync (and insert data):" + e.getMessage());
                    }
                }
            }));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        Log.info("[GATEWAY] Barrel sync completed");

        return true;
    }

    //---------------------------------------- END OF BARREL HANDLING METHODS -------------------------------------------------//

    //------------------------------------------------- CALLBACK METHODS ------------------------------------------------------//

    /**
     * Callback: Barrels notify state changes.
     * @param barrelPort Barrel port
     * @param status true if active, false if inactive
     * @throws RemoteException RMI exception
     */
    @Override
    public void reportBarrelStatus(int barrelPort, boolean status) throws RemoteException {
        if (status) {
            try {
                Registry registry = LocateRegistry.getRegistry(Config.BARREL_HOSTS_TRANSLATION_TABLE.get(barrelPort), barrelPort);
                BarrelInterface barrel = (BarrelInterface) registry.lookup("barrel");
                barrels.put(barrelPort, barrel);
                stats.updateBarrelIndexSize(barrelPort, stats.getActiveBarrels().getOrDefault(barrelPort, 0L));
                Log.info("[GATEWAY] Barrel " + barrelPort + " registered");
            } catch (NotBoundException | RemoteException e) {
                Log.error("[GATEWAY] Failed to register barrel " + barrelPort + ": " + e.getMessage());
            }
        } else {
            barrels.remove(barrelPort);
            stats.removeBarrelStats(barrelPort);
            Log.info("[GATEWAY] Barrel " + barrelPort + " unregistered");
        }

        try{
            this.queue.changeBarrelStatus(barrelPort, status);
            Log.info("[GATEWAY] Updated barrel " + barrelPort + " status to url queue");
        } catch (RemoteException e) {
            Log.warning("[GATEWAY] Failed to send barrel " + barrelPort + " status to url queue");
        }
    }

    /**
     * Callback: Barrels report index size updates for stats.
     * @param barrelPort Barrel port
     * @param indexSize  Current index size
     */
    @Override
    public void reportIndexStats(int barrelPort, int indexSize) throws RemoteException {
        stats.updateBarrelIndexSize(barrelPort, indexSize);
    }

    /**
     * Callback: Barrels report search completion time for response-time stats.
     * @param barrelPort Barrel port
     * @param query Query identifier
     * @param responseTime Response time in ms
     * @throws RemoteException RMI exception
     */
    @Override
    public void reportSearchStats(int barrelPort, String query, long responseTime) throws RemoteException {
        stats.updateQueryOccurrence(query);
        stats.updateSearchTime(barrelPort, responseTime);
        Log.info("[GATEWAY] Search completed on barrel " + barrelPort + " for query " + query + " in " + responseTime + " ms");
    }

    //------------------------------------------------ END OF CALLBACK METHODS -------------------------------------------//

    //------------------------------------------------ USER METHODS -------------------------------------------------------//

    /**
     * Index a URL at the URL queue.
     * @param url the URL to index
     * @throws RemoteException RMI exception
     */
    @Override
    public void index(String url) throws RemoteException {
        if (queue != null) {
            try {
                queue.addUrl(url, true);
                Log.info("[GATEWAY] Client added URL to queue: " + url);
            } catch (RemoteException e) {
                Log.error("[GATEWAY] Failed to add URL to queue: " + e.getMessage());
            }
        } else {
            Log.error("[GATEWAY] Queue is not available");
        }
    }

    /**
     * Search for a query, returning found pages.
     * @param query Search query
     * @return list of lists of pages (each inner list has up to 10 pages)
     * @throws RemoteException RMI exception
     */
    @Override
    @SuppressWarnings("BusyWait")
    public List<Page> search(String clientId, String query, int pageNumber, int filter, String domain) throws RemoteException {
        Log.info("[GATEWAY] Client " + clientId + " searching for query: " + query);

        String[] terms = Arrays.stream(query.split("\\s+"))
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .toArray(String[]::new);

        for (int attempt = 1; attempt <= Config.GATEWAY_RETRIES; attempt++) {
            Map.Entry<Integer, BarrelInterface> entry = selectBarrel();
            if (entry == null) {
                Log.warning("[GATEWAY] No available barrels for search on attempt " + attempt);
                return null;
            }

            BarrelInterface barrel = entry.getValue();
            int barrelPort = entry.getKey();

            try {
                List<Page> pages = barrel.searchQuery(query, terms, pageNumber, filter, domain);
                if (pages == null || pages.isEmpty()) {
                    return null;
                }

                Log.info("[GATEWAY] Client " + clientId + " search completed successfully on barrel " + barrelPort + " on attempt " + attempt);
                return pages;

            } catch (RemoteException e) {
                Log.error("[GATEWAY] Search failed on barrel " + barrelPort + ": " + e.getMessage() + " (attempt " + attempt + "). Retrying");

                if (attempt < Config.GATEWAY_RETRIES) {
                    try {
                        Thread.sleep((long) (Config.GATEWAY_BACKOFF * Math.pow(2, attempt - 1)));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        Log.error("[GATEWAY] Search not successful. All search attempts failed for query: " + query);
        return null;
    }

    /**
     * Get backlinks for a given page.
     * @param page Page to get backlinks for
     * @return list of backlink pages
     * @throws RemoteException RMI exception
     */
    @Override
    @SuppressWarnings("BusyWait")
    public List<Page> backlinks(String clientId, Page page) throws RemoteException {
        Log.info("[GATEWAY] Client " + clientId + " requesting backlinks for page: " + page.getUrl());

        for (int attempt = 1; attempt <= Config.GATEWAY_RETRIES; attempt++) {
            Map.Entry<Integer, BarrelInterface> entry = selectBarrel();
            if (entry == null) {
                Log.warning("[GATEWAY] No available barrels for backlinks on attempt " + attempt);
                return null;
            }

            BarrelInterface barrel = entry.getValue();
            int barrelPort = entry.getKey();

            try {
                List<Page> backlinks = barrel.getBacklinks(page);
                Log.info("[GATEWAY] Client " + clientId + " backlinks retrieved successfully from barrel " + barrelPort + " on attempt " + attempt);
                return backlinks;

            } catch (RemoteException e) {
                Log.error("[GATEWAY] Backlinks retrieval failed on barrel " + barrelPort + ": " + e.getMessage() + " (attempt " + attempt + "). Retrying");

                if (attempt < Config.GATEWAY_RETRIES) {
                    try {
                        Thread.sleep((long) (Config.GATEWAY_BACKOFF * Math.pow(2, attempt - 1)));
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        Log.error("[GATEWAY] Backlinks retrieval not successful. All attempts failed for page: " + page.getUrl());
        return null;
    }

    /**
     * Get stats from the gateway.
     * @return stats string
     * @throws RemoteException RMI exception
     */
    @Override
    public String stats(String clientId) throws RemoteException {
        Map<String, Long> topSearches = stats.getTopSearches();
        Map<Integer, Long> activeBarrels = stats.getActiveBarrels();
        Map<Integer, Long> responseTimes = stats.getAverageResponse();

        Log.info("[GATEWAY] Client " + clientId + " requested stats");

        StringBuilder sb = new StringBuilder();
        sb.append("=== Statistics ===\n\n");
        sb.append("Top 10 Searches:\n");
        for (Map.Entry<String, Long> entry : topSearches.entrySet()) {
            sb.append(String.format("  \"%s\" - %d times\n", entry.getKey(), entry.getValue()));
        }
        sb.append("Active Barrels:\n");
        for (Map.Entry<Integer, Long> entry : activeBarrels.entrySet()) {
            sb.append(String.format("  \"%s\" - %d indexes\n", entry.getKey(), entry.getValue()));
        }
        sb.append("Average Response Times:\n");
        for (Map.Entry<Integer, Long> entry : responseTimes.entrySet()) {
            sb.append(String.format("  \"%s\" - %d tenths\n", entry.getKey(), entry.getValue()));
        }

        Log.info("[GATEWAY] Client " + clientId + " stats retrieved successfully");

        return sb.toString();
    }
    //------------------------------------------------ END OF USER METHODS -------------------------------------------------------//

    /**
     * Main method for the Gateway.
     */
    public static void main(String[] args) {
        Log.clearLog();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

        try {
            Gateway gateway = new Gateway();

            if (gateway.queue == null) {
                Log.error("[GATEWAY] Exiting due to unavailable URL Queue");
                return;
            }

            Registry registry = LocateRegistry.createRegistry(Config.GATEWAY_PORT);
            registry.rebind("gateway", gateway);
            Log.info("[GATEWAY] Gateway ready on " + Config.GATEWAY_HOST + ":" + Config.GATEWAY_PORT);

            scheduler.scheduleAtFixedRate(() -> {
                try{
                    gateway.syncBarrels(0);
                } catch (RemoteException e) {
                    Log.warning("[GATEWAY] Periodic syncer will not be scheduled");
                }
                    }, 0, Config.GATEWAY_SYNC_INTERVAL, TimeUnit.MINUTES);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                gateway.stats.saveStats();
                Log.info("[GATEWAY] Stats saved successfully");
                Log.info("[GATEWAY] Gateway shutting down");
            }));
            
        } catch (RemoteException e) {
            Log.error("[GATEWAY] Failed to start Gateway RMI server: " + e.getMessage());
            System.exit(1);
        }
    }
}
