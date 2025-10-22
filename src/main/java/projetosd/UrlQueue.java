package projetosd;

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
    private final LinkedBlockingDeque<String> urlQueue = new LinkedBlockingDeque<>();

    /**
     * Constructs the UrlQueue.
     * @throws java.rmi.RemoteException RMI exception
     */
    public UrlQueue() throws java.rmi.RemoteException {
        super();
    }

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
        Debug.info("[URLQueue] Added to queue: " + url);
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

    /**
     * Main for UrlQueue. Starts the RMI registry and binds the queue.
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        try {
            UrlQueue queue = new UrlQueue();
            Registry registry = LocateRegistry.createRegistry(Ports.URL_QUEUE_PORT);
            registry.rebind("queue", queue);
            Debug.info("[URLQueue] RMI server ready.");
        } catch (RemoteException e) {
            Debug.error("[URLQueue] Exception: " + e.getMessage());
        }
    }
}
