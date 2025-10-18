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

/**
 * Implementation of the GatewayInterface for clients.
 * Exposes RMI methods for adding URLs and searching (paginated groups of 10).
 */
public class Gateway extends UnicastRemoteObject implements GatewayInterface {

    /*
     * Gateway RMI port.
     */
    private static final int GATEWAY_PORT = 1098;

    /*
     * Known barrel ports for discovery.
     */
    private static final int BARREL_PORTS[] = {8183};

    /*
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
        try {
            this.queue = (UrlQueueInterface) LocateRegistry.getRegistry(1099).lookup("queue");
        } catch (NotBoundException | RemoteException e) {
            Debug.error("UrlQueue not available at startup: " + e.getMessage());
            this.queue = null;
        }
        discoverBarrels();
    }

    /**
     * Discover barrels by trying to connect to known ports.
     * @throws RemoteException
     */
    private void discoverBarrels() {
        for (int port : BARREL_PORTS) {
            try {
                BarrelInterface barrel = (BarrelInterface) LocateRegistry.getRegistry(port).lookup("index");
                barrels.add(barrel);
                Debug.info("Discovered barrel on port " + port);
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
            return pageLists;

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
        return "Gateway connected barrels: " + barrels.size();
    }

    /**
     * Main method for the Gateway.
     */
    public static void main(String[] args) {
        try {
            Gateway server = new Gateway();

            Registry registry = LocateRegistry.createRegistry(GATEWAY_PORT);
            registry.rebind("gateway", server);
            Debug.info("Gateway ready on port " + GATEWAY_PORT);

        } catch (RemoteException e) {
            Debug.error("Failed to start Gateway: " + e.getMessage());
        }
    }
}
