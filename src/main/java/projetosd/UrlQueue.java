package projetosd;

import java.io.*;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Implementation of the UrlQueueInterface for managing a queue of URLs.
 * Handles addition and retrieval of URLs for distributed crawling.
 * 
 * @author Jose Amado & José Capinha
 * @version 1.0
 */
public class UrlQueue extends UnicastRemoteObject implements UrlQueueInterface {

    /**
     * Queue for storing URLs.
     */
    private final LinkedBlockingDeque<String> urlQueue;

    /**
     * Serial data file name
     */
    private static final String FILE_NAME = "data/urlQueue.ser";

    /**
     * Constructs the UrlQueue.
     * @throws java.rmi.RemoteException RMI exception
     */
    public UrlQueue() throws java.rmi.RemoteException {
        urlQueue = new LinkedBlockingDeque<>();
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
     * @return The next URL, or null if interrupted
     * @throws RemoteException RMI exception
     */
    @Override
    public String takeUrl() throws RemoteException {
        try {
            return urlQueue.take();
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
            Log.info("[URLQUEUE] Queue saved successfully.");
        } catch (IOException e) {
            Log.warning("[URLQUEUE] Could not save queue");
        }
    }

    /**
     * Method to attempt to load queue from serial file
     */
    @SuppressWarnings("unchecked")
    private void loadQueue(){
        File file = new File(FILE_NAME);
        if (!file.exists()) {
            Log.info("[URLQUEUE] No data file found, starting empty queue");
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            LinkedBlockingDeque<String> loadedQueue = (LinkedBlockingDeque<String>) ois.readObject();

            if(loadedQueue.isEmpty()){
                Log.info("[URLQUEUE] No data stored, starting new empty queue");
                return;
            }

            urlQueue.addAll(loadedQueue);
            Log.info("[URLQUEUE] Queue loaded with " + urlQueue.size() + " URLs.");
        } catch (IOException | ClassNotFoundException e) {
            Log.warning("[URLQUEUE] Error loading queue: " + e.getMessage());
        }
    }

    private void clearQueue(){
        urlQueue.clear();
    }

    //---------------------------------- END OF DATA MANAGEMENT METHODS -----------------------------------------//


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

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                queue.saveQueue();
                Log.info("[URLQueue] Exiting");
            }));            
        } catch (RemoteException e) {
            Log.error("[URLQueue] Exiting. Could not start RMI server: " + e.getMessage());
            System.exit(1);
        }
    }
}
