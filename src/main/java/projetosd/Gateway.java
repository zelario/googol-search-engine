package projetosd;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    /*
     * List of connected barrels.
     */
    private List<BarrelInterface> barrels = Collections.synchronizedList(new ArrayList<>());

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
            Debug.info("Connected to URL Queue on port " + Ports.URL_QUEUE_PORT);
        } catch (NotBoundException | RemoteException e) {
            Debug.error("URL Queue not available: " + e.getMessage());
        }
        discoverBarrels();
    }

    /**
     * Discover barrels by trying to connect to known ports.
     * @throws RemoteException
     */
    private void discoverBarrels() {
        for (int port : Ports.BARREL_PORTS) {
            try {
                BarrelInterface barrel = (BarrelInterface) LocateRegistry.getRegistry(port).lookup("barrel");
                try {
                    barrel.ping();
                    barrels.add(barrel);
                    Debug.info("Discovered barrel on port " + port);
                } catch (RemoteException e) {
                    Debug.error("Barrel on port " + port + " is not responding.");
                }
            } catch (NotBoundException | RemoteException e) {
                Debug.error("No barrel on port: " + port + " -> " + e.getMessage());
            }
        }
    }

    /**
     * Select an available barrel by pinging them.
     * @return an available barrel or null
     * @throws RemoteException
     */
    private BarrelInterface selectAvailableBarrel() {

        List<BarrelInterface> copy = new ArrayList<>(barrels);
        while (!copy.isEmpty()) {
            int idx = (int) (Math.random() * copy.size());
            BarrelInterface barrel = copy.get(idx);
            try {
                barrel.ping();
                Debug.info("Selected barrel: " + barrel);
                return barrel;
            } catch (RemoteException e) {
                Debug.error("Barrel not available: " + e.getMessage());
                copy.remove(idx);
            }
        }
        return null;
    }

    //------------------ CALLBACK FUNCTIONS ------------------//

    /**
     * Callback: Barrels send periodic stats updates.
     * @param barrelPort Barrel port
     * @param indexSize  Current index size
     * @param urlsParsed Total URLs parsed
     */
    @Override
    public void callbackStats(int barrelPort, int indexSize, long urlsParsed) throws RemoteException {
        String barrelId = "barrel-" + barrelPort;
        stats.updateBarrelIndexSize(barrelId, indexSize);
        Debug.info("Stats updated from " + barrelId + ": indexSize=" + indexSize + ", urlsParsed=" + urlsParsed);
    }

    /**
     * Callback: Barrels notify active/inactive state changes.
     * @param barrelPort Barrel port
     * @param isActive true if active, false if inactive
     * @throws RemoteException
     */
    @Override
    public void callbackBarrelStatus(int barrelPort, boolean isActive) throws RemoteException {
        String barrelId = "barrel-" + barrelPort;
        if (isActive) {
            // Mark/refresh as active (size 0 until next update arrives)
            stats.updateBarrelIndexSize(barrelId, 0);
            Debug.info("Barrel active: " + barrelId);
        } else {
            stats.removeBarrel(barrelId);
            Debug.warning("Barrel inactive: " + barrelId);
        }
    }

    /**
     * Callback: Barrels report search completion time for response-time stats.
     * @param barrelPort Barrel port
     * @param queryId Query identifier
     * @param responseTime Response time in ms
     * @throws RemoteException
     */
    @Override
    public void callbackSearchCompleted(int barrelPort, String queryId, long responseTime) throws RemoteException {
        String barrelId = "barrel-" + barrelPort;
        stats.updateSearchTime(barrelId, responseTime);
        Debug.info("Search completed on " + barrelId + " for query " + queryId + " in " + responseTime + " ms");
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
                Debug.info("Client added URL to queue: " + url);
            } catch (RemoteException e) {
                Debug.error("Failed to add URL to queue: " + e.getMessage());
            }
        } else {
            Debug.error("Queue is not available.");
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
            Debug.warning("No available barrels for search.");
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
            Debug.info("Search completed successfully.");
            return pageLists; //TODO backlinks depois do resultado

        } catch (RemoteException e) {
            Debug.error("Search failed on barrel: " + e.getMessage());
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
        Map<String, Long> activeBarrels = stats.getActiveBarrels();
        Map<String, Long> responseTimes = stats.getAverageResponse();

        StringBuilder sb = new StringBuilder();
        sb.append("=== Gateway Statistics ===\n\n");
        sb.append("Top 10 Searches:\n");
        for (Map.Entry<String, Long> entry : topSearches.entrySet()) {
            sb.append(String.format("  \"%s\" - %d times\n", entry.getKey(), entry.getValue()));
        }
        sb.append("Active Barrels:\n");
        for (Map.Entry<String, Long> entry : activeBarrels.entrySet()) {
            sb.append(String.format("  \"%s\" - %d indexes\n", entry.getKey(), entry.getValue()));
        }
        sb.append("Average Response Times (tenths of seconds):\n");
        for (Map.Entry<String, Long> entry : responseTimes.entrySet()) {
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
            Debug.info("Gateway ready on port " + Ports.GATEWAY_PORT);

        } catch (RemoteException e) {
            Debug.error("Failed to start Gateway: " + e.getMessage());
        }
    }
}
