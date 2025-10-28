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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
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

    /**
     * Constructs the Gateway.
     * @throws RemoteException RMI exception
     */
    public Gateway() throws RemoteException {
        stats = new Stats();

        try {
            Registry registry = LocateRegistry.getRegistry(Config.URL_QUEUE_PORT);
            queue = (UrlQueueInterface) registry.lookup("queue");
            Log.info("[GATEWAY] Connected to URL Queue on port " + Config.URL_QUEUE_PORT);
        } catch (NotBoundException | RemoteException e) {
            Log.error("[GATEWAY] URL Queue not available: " + e.getMessage());
            queue = null;
        }

        barrels = new ConcurrentHashMap<>();
    }

    /**
     * Select an available barrel entry (port + barrel instance) by pinging them.
     * @return Map entry of selected barrel port and instance, or null if none available
     */
    @SuppressWarnings("BusyWait")
    private Map.Entry<Integer, BarrelInterface> selectBarrel() { 
        List<Map.Entry<Integer, BarrelInterface>> entries = new ArrayList<>(barrels.entrySet());
        while (!entries.isEmpty()) {
            int index = (int) (Math.random() * entries.size());
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
                        break;
                    }
                }
            }
            
            if (available) {
                Log.info("[GATEWAY] Chosen barrel is fine. Selected barrel " + port);
                return entry;
            } else {
                Log.error("[GATEWAY] Barrel " + port + " not available after retries. Removing from registry.");
                entries.remove(index);
                barrels.remove(port);
                stats.removeBarrelStats(port);
            }
        }
        return null;
    }

    /**
     * Method to get barrel all hashes to check consistency
     * @return Map with ports as keys and as values hash maps with tables as keys as the MD5 hashes as values
     */
    private Map<Integer, Map<String, String>> barrelHashes(Timestamp now){
        Map<Integer, Map<String, String>> barrelHashes = new HashMap<>();

        for(Integer barrelPort : Config.BARREL_PORTS) {
            try{
                BarrelInterface barrel = (BarrelInterface) LocateRegistry.getRegistry(barrelPort).lookup("barrel");
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
     * @return HashMap with ports as keys and a list of table names where mismatches were found as value
     */
    private List<String>  barrelMismatches(Timestamp now){
        List<String> mismatches = new ArrayList<>();
        Map<Integer, Map<String, String>> allHashes = this.barrelHashes(now);

        // No more than 1 barrel, no sync needed
        if (allHashes.size() <= 1) return mismatches;

        // Get a reference db to check against the others
        int referencePort = allHashes.keySet().stream().findFirst().orElse(null);

        // Store reference hashes
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

        return mismatches;
    }

    @Override
    @SuppressWarnings("BusyWait")
    public void synchBarrels() throws RemoteException {
        Timestamp syncTime = Timestamp.valueOf(LocalDateTime.now());
        List<String> mismatches = barrelMismatches(syncTime);

        if(mismatches.isEmpty()){
            Log.info("[GATEWAY] Found no mismatches in barrels");
            return;
        }

        Log.warning("[GATEWAY] Barrel mismatch found");

        // Get tables row hashes to find missing info
        // barrelPort -> <row hash -> row content>
        Map<Integer, Map<String, Map<String, String>>> rowHashes = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // FIX: rowHashes is empty for some reason
        for(Integer barrelPort : Config.BARREL_PORTS){
            futures.add(CompletableFuture.runAsync(() -> {
                for(int i = 0; i < Config.GATEWAY_RETRIES; i++){
                    try{
                        BarrelInterface b = (BarrelInterface) LocateRegistry.getRegistry(barrelPort).lookup("barrel");
                        b.ping();
                        Map<String, Map<String, String>> barrelRows = new HashMap<>();

                        for (String table : mismatches) {
                            barrelRows.put(table, b.getMD5Hash(table, syncTime));
                        }

                        rowHashes.put(barrelPort, barrelRows);
                    } catch (NotBoundException | RemoteException e) {
                        if (i < Config.GATEWAY_RETRIES) {
                            try {
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

        // finally introduce missing data into barrels by filtering which rows are not present in each barrel
        futures.clear();
        for (Integer barrelPort : rowHashes.keySet()) {
            Map<String, Map<String, String>> barrelData = rowHashes.get(barrelPort);
            futures.add(CompletableFuture.runAsync(() -> {
                for(int i = 0; i < Config.GATEWAY_RETRIES; i++){
                    try {
                        BarrelInterface b = (BarrelInterface) LocateRegistry.getRegistry(barrelPort).lookup("barrel");
                        b.ping();

                        for (String table : barrelData.keySet()) {
                            Map<String, String> barrelTable = barrelData.get(table);
                            Map<String, String> missingRows = globalRows.get(table).entrySet().stream()
                                    .filter(e -> !barrelTable.containsKey(e.getKey()))
                                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

                            if (!missingRows.isEmpty()) {
                                b.insertMissingRows(table, missingRows.values());
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
    }

    //------------------ CALLBACK FUNCTIONS ------------------//

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
                Registry registry = LocateRegistry.getRegistry(barrelPort);
                BarrelInterface barrel = (BarrelInterface) registry.lookup("barrel");
                barrels.put(barrelPort, barrel);
                stats.updateBarrelIndexSize(barrelPort, 0L);
                Log.info("[GATEWAY] Barrel " + barrelPort + " registered.");
            } catch (NotBoundException | RemoteException e) {
                Log.error("[GATEWAY] Failed to register barrel " + barrelPort + ": " + e.getMessage());
            }
        } else {
            barrels.remove(barrelPort);
            stats.removeBarrelStats(barrelPort);
            Log.info("[GATEWAY] Barrel " + barrelPort + " unregistered.");
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
        Log.info("[GATEWAY] Stats updated from barrel " + barrelPort + ": indexSize=" + indexSize);
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

    //---------------- END CALLBACK FUNCTIONS -------------//

    //------------------ USER FUNCTIONS ------------------//

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
            Log.error("[GATEWAY] Queue is not available.");
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
    public String search(String clientId, String query, int pageNumber) throws RemoteException {
        Log.info("[GATEWAY] Client " + clientId + " searching for query: " + query);

        String[] terms = Arrays.stream(query.split("\\s+"))
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .toArray(String[]::new);

        for (int attempt = 1; attempt <= Config.GATEWAY_RETRIES; attempt++) {
            Map.Entry<Integer, BarrelInterface> entry = selectBarrel();
            if (entry == null) {
                Log.warning("[GATEWAY] No available barrels for search on attempt " + attempt + ".");
                return "No results found.";
            }

            BarrelInterface barrel = entry.getValue();
            int barrelPort = entry.getKey();
            try {
                List<Page> pages = barrel.searchQuery(query, terms, pageNumber);
                if (pages == null || pages.isEmpty()) {
                    return "\nNo results found.\n";
                }

                StringBuilder sb = new StringBuilder();
                int startIndex = (pageNumber - 1) * 10 + 1;
                sb.append(String.format("\n- Page %d:\n\n", pageNumber));

                for (int i = 0; i < pages.size(); i++) {
                    Page p = pages.get(i);
                    int num = startIndex + i;
                    sb.append(String.format("%d) %s\n", num, p.getTitle() == null || p.getTitle().isBlank() ? "(no title)" : p.getTitle()));
                    sb.append(String.format("   %s\n", p.getUrl()));
                    String snippet = p.getSnippet() == null ? "" : p.getSnippet();
                    if (!snippet.isBlank()) {
                        sb.append(String.format("   \"%s\"\n", snippet.length() > 200 ? snippet.substring(0, 200) + "..." : snippet));
                    }
                }

                Log.info("[GATEWAY] Client " + clientId + " search completed successfully on barrel " + barrelPort + " on attempt " + attempt + ".");
                return sb.toString();

            } catch (RemoteException e) {
                Log.error("[GATEWAY] Search failed on barrel " + barrelPort + ": " + e.getMessage() + " (attempt " + attempt + "). Retrying.");

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
        return "No results found.";
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

        Log.info("[GATEWAY] Client " + clientId + " requested stats.");

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

        Log.info("[GATEWAY] Client " + clientId + " stats retrieved successfully.");

        Log.info(sb.toString());
        return sb.toString();
    }
    //------------------ END OF USER FUNCTIONS ------------------//

    /**
     * Main method for the Gateway.
     */
    public static void main(String[] args) {
        Log.clearLog();
        try {
            Gateway gateway = new Gateway();

            if (gateway.queue == null) {
                Log.error("[GATEWAY] Exiting due to unavailable URL Queue.");
                return;
            }

            Registry registry = LocateRegistry.createRegistry(Config.GATEWAY_PORT);
            registry.rebind("gateway", gateway);
            Log.info("[GATEWAY] Gateway ready on port " + Config.GATEWAY_PORT);

        } catch (RemoteException e) {
            Log.error("[GATEWAY] Failed to start Gateway: " + e.getMessage());
            System.exit(1);
        }
    }
}
