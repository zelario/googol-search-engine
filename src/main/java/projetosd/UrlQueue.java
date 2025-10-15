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
 * @author Jose Amado e José Capinha
 * @version 1.0
 */
public class UrlQueue extends UnicastRemoteObject implements UrlQueueInterface {

    /**
     * Queue for storing URLs.
     */
    private final LinkedBlockingDeque<String> urlQueue = new LinkedBlockingDeque<>();

    /**
     * Constructs the UrlQueue.
     * @throws java.rmi.RemoteException 
     */
    public UrlQueue() throws java.rmi.RemoteException {
        super();
    }

    /**
     * Main for UrlQueue. Starts the RMI registry and binds the queue.
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        try {
            UrlQueue queue = new UrlQueue();

            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("queue", queue);
            System.out.println("Queue ready");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a URL to the queue. User input URLs are prioritized.
     * @param url The URL to add
     * @param userInput True if the URL was provided by the user, false otherwise
     * @throws RemoteException
     */
    @Override
    public void addUrl(String url, boolean userInput) throws RemoteException {
        // If userInput -> insert first so user input is processed first, if not, insert normally (FIFO)
        if (userInput) {
            urlQueue.addFirst(url);
        } else {
            urlQueue.add(url);
        }

        System.out.println("[urlQueue] Added url: " + url);
    }

    /**
     * Retrieves and removes the next URL from the queue.
     * @return The next URL, or null if interrupted
     * @throws RemoteException
     */
    @Override
    public String takeUrl() throws RemoteException {
        try {
            return urlQueue.take();
        } catch (InterruptedException e) {
            return null;
        }
    }

}
