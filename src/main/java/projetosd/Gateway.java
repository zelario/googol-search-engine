package projetosd;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of the GatewayInterface for clients.
 * Exposes RMI methods for adding URLs and searching.
 * 
 * @author Jose Amado & José Capinha
 * @version 1.0
 */
public class Gateway extends UnicastRemoteObject implements GatewayInterface {

    /*
     * Stats object to track various statistics.
     */
    private Stats stats;

    /**
     * Reference to the URL queue.
     */
    private UrlQueueInterface queue;

    /**
     * Map of registered barrels by their port number.
     */
    private final Map<Integer, BarrelInterface> barrels = new ConcurrentHashMap<>();

    /**
     * Ping retry attempts.
     */
    private static final int PING_RETRIES = 3;

    /**
     * Search retry attempts.
     */
    private static final int SEARCH_RETRIES = 2;

    /**
     * Initial backoff time in milliseconds for both ping and search retries.
     */
    private static final long BACKOFF_TIME = 200;

    /**
     * Constructs the Gateway.
     * @throws RemoteException RMI exception
     */
    public Gateway() throws RemoteException {
        super();
        stats = new Stats();
        try {
            Registry registry = LocateRegistry.getRegistry(Ports.URL_QUEUE_PORT);
            queue = (UrlQueueInterface) registry.lookup("queue");
            Debug.info("[GATEWAY] Connected to URL Queue on port " + Ports.URL_QUEUE_PORT);
        } catch (NotBoundException | RemoteException e) {
            Debug.error("[GATEWAY] URL Queue not available: " + e.getMessage());
        }
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
            long backoff = BACKOFF_TIME;
            while (attempts < PING_RETRIES) {
                try {
                    barrel.ping();
                    available = true;
                    break;
                } catch (RemoteException e) {
                    attempts++;
                    Debug.warning("[GATEWAY] Ping failed for barrel " + port + " on attempt " + attempts + ": " + e.getMessage());
                    if (attempts >= PING_RETRIES) break;
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    backoff *= 2;
                }
            }
            
            if (available == true) {
                Debug.info("[GATEWAY] Chosen barrel is fine. Selected barrel on port " + port);
                return entry;
            } else {
                Debug.error("[GATEWAY] Barrel on port " + port + " not available after retries. Removing from registry.");
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
                Debug.info("[GATEWAY] Barrel registered on port " + barrelPort);
            } catch (NotBoundException | RemoteException e) {
                Debug.error("[GATEWAY] Failed to register barrel on port " + barrelPort + ": " + e.getMessage());
            }
        } else {
            barrels.remove(barrelPort);
            stats.removeBarrelStats(barrelPort);
            Debug.info("[GATEWAY] Barrel unregistered on port " + barrelPort);
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
        Debug.info("[GATEWAY] Stats updated from barrel " + barrelPort + ": indexSize=" + indexSize + ", urlsParsed=" + urlsParsed);
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
        Debug.info("[GATEWAY] Search completed on barrel" + barrelPort + " for query " + query + " in " + responseTime + " ms");
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
                Debug.info("[GATEWAY] Client added URL to queue: " + url);
            } catch (RemoteException e) {
                Debug.error("[GATEWAY] Failed to add URL to queue: " + e.getMessage());
            }
        } else {
            Debug.error("[GATEWAY] Queue is not available.");
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
        Debug.info("[GATEWAY] Client " + clientId + " searching for query: " + query);

        String[] terms = Arrays.stream(query.split("\\s+"))
                .filter(s -> !s.isBlank())
                .map(String::toLowerCase)
                .toArray(String[]::new);

        long backoff = BACKOFF_TIME;
        for (int attempt = 1; attempt <= SEARCH_RETRIES; attempt++) {
            Map.Entry<Integer, BarrelInterface> entry = selectBarrel();
            if (entry == null) {
                Debug.warning("[GATEWAY] No available barrels for search on attempt " + attempt + ".");
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
                Debug.info("[GATEWAY] Client " + clientId + " search completed successfully on barrel " + barrelPort + " on attempt " + attempt + ".");
                return pageLists;

            } catch (RemoteException e) {
                Debug.error("[GATEWAY] Search failed on barrel " + barrelPort + ": " + e.getMessage() + " (attempt " + attempt + "). Retrying.");

                if (attempt < SEARCH_RETRIES) {
                    try {
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    backoff *= 2;
                }
            }
        }

        Debug.error("[GATEWAY] Search not successful. All search attempts failed for query: " + query);
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

        Debug.info("[GATEWAY] Client " + clientId + " requested stats.");

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

        Debug.info("[GATEWAY] Client " + clientId + " stats retrieved successfully.");

        return sb.toString();
    }
    //------------------ END OF USER FUNCTIONS ------------------//

    /**
     * Main method for the Gateway.
     */
    public static void main(String[] args) {
        try {
            Gateway server = new Gateway();

            Registry registry = LocateRegistry.createRegistry(Ports.GATEWAY_PORT);
            registry.rebind("gateway", server);
            Debug.info("[GATEWAY] Gateway ready on port " + Ports.GATEWAY_PORT);

        } catch (RemoteException e) {
            Debug.error("[GATEWAY] Failed to start Gateway: " + e.getMessage());
        }
    }
}
