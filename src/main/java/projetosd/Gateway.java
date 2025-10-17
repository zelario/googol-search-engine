package projetosd;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

/**
 * Implementation of the GatewayInterface for clients.
 * Exposes RMI methods for adding URLs, searching, and getting stats.
 * 
 * @author Jose Amado e José Capinha
 * @version 1.0
 */
public class Gateway extends UnicastRemoteObject implements GatewayInterface {

    private static final int GATEWAY_PORT = 1098;
    private final List<BarrelInterface> barrels = Collections.synchronizedList(new ArrayList<>());

    private UrlQueueInterface queue;

    public Gateway() throws RemoteException {
        super();
        try {
            this.queue = (UrlQueueInterface) LocateRegistry.getRegistry(1099).lookup("queue");
        } catch (Exception e) {
            Debug.warning("UrlQueue not available at startup: " + e.getMessage());
            this.queue = null;
        }
        discoverBarrels();
    }

    private void discoverBarrels() {
        int[] ports = {8183};
        for (int port : ports) {
            try {
                BarrelInterface barrel = (BarrelInterface) LocateRegistry.getRegistry(port).lookup("index");
                barrels.add(barrel);
                Debug.info("Discovered barrel on port " + port);
            } catch (Exception e) {
                Debug.warning("No barrel on port: " + port + " -> " + e.getMessage());
            }
        }
    }

    private BarrelInterface selectAvailableBarrel() {
        synchronized (barrels) {
            List<BarrelInterface> copy = new ArrayList<>(barrels);
            while (!copy.isEmpty()) {
                int idx = (int) (Math.random() * copy.size());
                BarrelInterface b = copy.get(idx);
                try {
                    b.ping();
                    return b;
                } catch (Exception e) {
                    copy.remove(idx);
                }
            }
        }
        return null;
    }

    @Override
    public boolean addUrl(String url) throws RemoteException {

    }

    @Override
    public List<String> search(String term, int page) throws RemoteException {

    }

    @Override
    public String getStats() throws RemoteException {

    }

    public static void main(String[] args) {
        try {
            Gateway server = new Gateway();

            Registry registry = LocateRegistry.createRegistry(GATEWAY_PORT);
            registry.rebind("gateway", server);
            System.out.println("Gateway RMI server ready on port " + GATEWAY_PORT + " (name 'gateway')");

            try (Scanner scanner = new Scanner(System.in)) {
                boolean run = true;
                while (run) {

                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
