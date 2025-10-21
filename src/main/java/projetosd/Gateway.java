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
 * Exposes RMI methods for adding URLs and searching (paginated groups of 10).
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
     * Constructs the Gateway.
     * @throws RemoteException 
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
     * Select an available barrel by pinging them.
     * @return an available barrel or null
     */
    private BarrelInterface selectAvailableBarrel() {
        List<Map.Entry<Integer, BarrelInterface>> entries = new ArrayList<>(barrels.entrySet());
        while (!entries.isEmpty()) {
            int idx = (int) (Math.random() * entries.size());
            Map.Entry<Integer, BarrelInterface> entry = entries.get(idx);
            try {
                entry.getValue().ping();
                Debug.info("[GATEWAY] Selected barrel: port " + entry.getKey());
                return entry.getValue();
            } catch (RemoteException e) {
                Debug.error("[GATEWAY] Barrel on port " + entry.getKey() + " not available: " + e.getMessage());
                entries.remove(idx);
                barrels.remove(entry.getKey());
            }
        }
        return null;
    }

    //------------------ CALLBACK FUNCTIONS ------------------//

    /**
     * Callback: Barrels notify state changes.
     * @param barrelPort Barrel port
     * @param status true if active, false if inactive
     * @throws RemoteException
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
    public void callbackStats(int barrelPort, int indexSize, long urlsParsed) throws RemoteException {
        stats.updateBarrelIndexSize(barrelPort, indexSize);
        Debug.info("[GATEWAY] Stats updated from barrel " + barrelPort + ": indexSize=" + indexSize + ", urlsParsed=" + urlsParsed);
    }

    /**
     * Callback: Barrels report search completion time for response-time stats.
     * @param barrelPort Barrel port
     * @param queryId Query identifier
     * @param responseTime Response time in ms
     * @throws RemoteException
     */
    @Override
    public void callbackSearchCompleted(int barrelPort, String query, long responseTime) throws RemoteException {
        stats.updateSearchTime(barrelPort, responseTime);
        Debug.info("[GATEWAY] Search completed on barrel" + barrelPort + " for query " + query + " in " + responseTime + " ms");
    }

    //---------------- END CALLBACK FUNCTIONS -------------//

    //------------------ USER FUNCTIONS ------------------//

    /**
     * Index a URL at the URL queue.
     * @param url the URL to index
     * @throws RemoteException
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
     * @throws RemoteException 
     */
    @Override
    public List<List<Page>> search(String query) throws RemoteException {
        BarrelInterface barrel = selectAvailableBarrel();
        if (barrel == null) {
            Debug.warning("[GATEWAY] No available barrels for search.");
            return new ArrayList<>();
        }

        try {
            String[] terms = Arrays.stream(query.split("\\s+"))
                    .filter(s -> !s.isBlank())
                    .map(String::toLowerCase)
                    .toArray(String[]::new);

            List<Page> pages = barrel.searchQuery(terms);
            if (pages == null || pages.isEmpty()) {
                return new ArrayList<>();
            }

            List<List<Page>> pageLists = new ArrayList<>();
            for (int i = 0; i < pages.size(); i += 10) {
                int to = Math.min(i + 10, pages.size());
                pageLists.add(new ArrayList<>(pages.subList(i, to)));
            }
            Debug.info("[GATEWAY] Search completed successfully.");
            return pageLists; //TODO backlinks depois do resultado

        } catch (RemoteException e) {
            Debug.error("[GATEWAY] Search failed on barrel: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Get stats from the gateway.
     * @return stats string
     * @throws RemoteException 
     */
    @Override
    public String stats() throws RemoteException {
        Map<String, Long> topSearches = stats.getTopSearches();
        Map<Integer, Long> activeBarrels = stats.getActiveBarrels();
        Map<Integer, Long> responseTimes = stats.getAverageResponse();

        StringBuilder sb = new StringBuilder();
        sb.append("=== Gateway Statistics ===\n\n");
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
