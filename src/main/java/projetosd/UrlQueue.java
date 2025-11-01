package projetosd;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collections;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Implementation of the UrlQueueInterface for managing a queue of URLs.
 * Handles addition and retrieval of URLs for distributed crawling.
 */
public class UrlQueue extends UnicastRemoteObject implements UrlQueueInterface {

    /**
     * Queue for storing URLs.
     */
    private final LinkedBlockingDeque<String> urlQueue;

    /**
     * Gateway reference for communication with other components.
     */
    private GatewayInterface gateway;

    /**
     * Serial data file name
     */
    private static final String FILE_NAME = "data/urlQueue.ser";

    /**
     * Map of registered barrels by their port number.
     */
    private final Map<Integer, BarrelInterface> barrels;

    /**
     * Constructs the UrlQueue.
     * @throws java.rmi.RemoteException RMI exception
     */
    public UrlQueue() throws java.rmi.RemoteException {
        urlQueue = new LinkedBlockingDeque<>();
        barrels = new ConcurrentHashMap<>();
        loadQueue();
    }

    //---------------------------------- URL QUEUE MANAGEMENT METHODS -----------------------------------------//

    /**
     * Adds a URL to the queue. User input URLs are prioritized.
     * @param url The URL to add
     * @param userInput True if the URL was provided by the user, false otherwise
     * @throws RemoteException RMI exception
     */
    @Override
    public void addUrl(String url, boolean userInput) throws RemoteException {
        if (userInput) {
            urlQueue.addFirst(url);
        } else {
            urlQueue.add(url);
        }
        Log.url("[URLQueue] Added to queue: " + url);
    }

    /**
     * Retrieves and removes the next URL from the queue.
     * @param downloaderBarrels Downloader list to be updated for reliable multicast
     * @return The updated barrel list and the url removed
     * @throws java.rmi.RemoteException RMI Exception
     */
    @Override
    public Map<Map<Integer, BarrelInterface>, String> takeUrl(Map<Integer, BarrelInterface> downloaderBarrels) throws RemoteException {
        try {
            downloaderBarrels.clear();
            downloaderBarrels.putAll(this.barrels);

            String url = urlQueue.takeFirst();

            return Collections.singletonMap(downloaderBarrels, url);
        } catch (InterruptedException e) {
            return null;
        }
    }

    //---------------------------------- END OF URL QUEUE MANAGEMENT METHODS -----------------------------------------//

    //---------------------------------- DATA MANAGEMENT METHODS -----------------------------------------//

    /**
     * Method to attempt to save queue data into file
     */
    private void saveQueue(){
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE_NAME))) {
            oos.writeObject(urlQueue);
            Log.info("[URLQueue] Queue saved successfully.");
        } catch (IOException e) {
            Log.warning("[URLQueue] Could not save queue");
        }
    }

    /**
     * Method to attempt to load queue from serial file
     */
    @SuppressWarnings("unchecked")
    private void loadQueue(){
        File file = new File(FILE_NAME);
        if (!file.exists()) {
            Log.info("[URLQueue] No data file found, starting empty queue");
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            LinkedBlockingDeque<String> loadedQueue = (LinkedBlockingDeque<String>) ois.readObject();

            if(loadedQueue.isEmpty()){
                Log.info("[URLQueue] No data stored, starting new empty queue");
                return;
            }

            urlQueue.addAll(loadedQueue);
            Log.info("[URLQueue] Queue loaded with " + urlQueue.size() + " URLs.");
        } catch (IOException | ClassNotFoundException e) {
            Log.warning("[URLQueue] Error loading queue: " + e.getMessage());
        }
    }

    /**
     * Clears all data in the queue
     */
    private void clearQueue(){
        urlQueue.clear();
    }

    //---------------------------------- END OF DATA MANAGEMENT METHODS -----------------------------------------//

    //---------------------------------- BARREL MANAGEMENT ----------------------------------//

    @Override
    public void changeBarrelStatus(int barrelPort, boolean status) throws java.rmi.RemoteException{
        if(status){
            try{
                Registry registry = LocateRegistry.getRegistry(Config.BARREL_HOSTS_TRANSLATION_TABLE.get(barrelPort), barrelPort);
                BarrelInterface barrel = (BarrelInterface) registry.lookup("barrel");

                this.barrels.put(barrelPort, barrel);
                Log.info("[URLQueue] Barrel " + barrelPort + " registered");
            }
            catch (RemoteException | NotBoundException e){
                Log.warning("[URLQueue] Failed to register barrel " + barrelPort);
            }
        }
        else {
            this.barrels.remove(barrelPort);
            Log.info("[URLQueue] Barrel " + barrelPort + " removed");
        }
    }

    //---------------------------------- END OF BARREL MANAGEMENT ----------------------------------//

    /**
     * Main for UrlQueue. Starts the RMI registry and binds the queue.
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        try {
            UrlQueue queue = new UrlQueue();
            Registry registry = LocateRegistry.createRegistry(Config.URL_QUEUE_PORT);
            registry.rebind("queue", queue);
            Log.info("[URLQueue] RMI server ready");

            registry = LocateRegistry.getRegistry(Config.GATEWAY_HOST, Config.GATEWAY_PORT);
            queue.gateway = (GatewayInterface) registry.lookup("gateway");
            queue.gateway.reportQueueStatus(true);
            Log.info("[URLQueue] Connected to Gateway on port " + Config.GATEWAY_PORT);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                queue.saveQueue();
                
                try {
                    queue.gateway.reportQueueStatus(false);
                } catch (RemoteException e) {
                    Log.error("[URLQueue] Could not notify Gateway of shutdown: " + e.getMessage());
                }

                Log.info("[URLQueue] Exiting");
            }));

            try (Scanner scanner = new Scanner(System.in)) {
                while (true) {
                    String input = scanner.nextLine();
                    if (input.isEmpty()) {
                        queue.clearQueue();
                        Log.info("[URLQueue] Queue cleared by user.");
                    }
                }
            }
            catch (Exception e) {
                Log.error("[URLQueue] Error in input handling: " + e.getMessage());
            }
            
        } catch (RemoteException | NotBoundException e) {
            Log.error("[URLQueue] Exiting. Could not start RMI server: " + e.getMessage());
            System.exit(1);
        }
    }
}
