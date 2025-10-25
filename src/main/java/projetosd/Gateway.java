package projetosd;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

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
            
            if (available == true) {
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

    //------------------ CALLBACK FUNCTIONS ------------------//

    /**
     * Callback: Barrels notify state changes.
     * @param barrelPort Barrel port
     * @param status true if active, false if inactive
     * @throws RemoteException RMI exception
     */
    @Override
    public void callbackBarrelStatus(int barrelPort, boolean status) throws RemoteException {
        if (status) {
            try {
                Registry registry = LocateRegistry.getRegistry(barrelPort);
                BarrelInterface barrel = (BarrelInterface) registry.lookup("barrel");
                barrels.put(barrelPort, barrel);
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
     * Callback: Barrels send periodic stats updates.
     * @param barrelPort Barrel port
     * @param indexSize  Current index size
     * @param urlsParsed Total URLs parsed
     */
    @Override
    public void callbackBarrelStats(int barrelPort, int indexSize, long urlsParsed) throws RemoteException {
        stats.updateBarrelIndexSize(barrelPort, indexSize);
        Log.info("[GATEWAY] Stats updated from barrel " + barrelPort + ": indexSize=" + indexSize + ", urlsParsed=" + urlsParsed);
    }

    /**
     * Callback: Barrels report search completion time for response-time stats.
     * @param barrelPort Barrel port
     * @param query Query identifier
     * @param responseTime Response time in ms
     * @throws RemoteException RMI exception
     */
    @Override
    public void callbackSearchCompleted(int barrelPort, String query, long responseTime) throws RemoteException {
        stats.updateQueryOccurrence(query);
        stats.updateSearchTime(barrelPort, responseTime);
        Log.info("[GATEWAY] Search completed on barrel" + barrelPort + " for query " + query + " in " + responseTime + " ms");
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
    public List<List<Page>> search(String clientId, String query) throws RemoteException {
        Log.info("[GATEWAY] Client " + clientId + " searching for query: " + query);

        String[] terms = Arrays.stream(query.split("\\s+"))
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .toArray(String[]::new);

        for (int attempt = 1; attempt <= Config.GATEWAY_RETRIES; attempt++) {
            Map.Entry<Integer, BarrelInterface> entry = selectBarrel();
            if (entry == null) {
                Log.warning("[GATEWAY] No available barrels for search on attempt " + attempt + ".");
                return new ArrayList<>();
            }

            BarrelInterface barrel = entry.getValue();
            int barrelPort = entry.getKey();
            try {
                List<Page> pages = barrel.searchQuery(query, terms);
                if (pages == null || pages.isEmpty()) {
                    return new ArrayList<>();
                }

                List<List<Page>> pageLists = new ArrayList<>();
                for (int i = 0; i < pages.size(); i += 10) {
                    int to = Math.min(i + 10, pages.size());
                    pageLists.add(new ArrayList<>(pages.subList(i, to)));
                }
                Log.info("[GATEWAY] Client " + clientId + " search completed successfully on barrel " + barrelPort + " on attempt " + attempt + ".");
                return pageLists;

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
        return new ArrayList<>();
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
        sb.append("Average Response Times (tenths of seconds):\n");
        for (Map.Entry<Integer, Long> entry : responseTimes.entrySet()) {
            sb.append(String.format("  \"%s\" - %d tenths\n", entry.getKey(), entry.getValue()));
        }

        Log.info("[GATEWAY] Client " + clientId + " stats retrieved successfully.");

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
        }
    }

    // TODO: complete
    public synchronized Map<Integer, Map<String, String>> getAllHashes(int ownPort) throws RemoteException {
        return new HashMap<Integer, Map<String, String>>();
    }
}
