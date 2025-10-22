package projetosd;

import java.rmi.Remote;

/**
 * Remote interface for a distributed URL queue.
 * 
 * @author Jose Amado & José Capinha
 * @version 1.0
 */
public interface UrlQueueInterface extends Remote {
    /**
     * Retrieves and removes the next URL from the queue.
     * @return The next URL
     * @throws java.rmi.RemoteException
     */
    String takeUrl() throws java.rmi.RemoteException;

    /**
     * Adds a URL to the queue. User input URLs are prioritized.
     * @param url The URL to add
     * @param userInput True if the URL was provided by the user, false otherwise
     * @throws java.rmi.RemoteException
     */
    void addUrl(String url, boolean userInput) throws java.rmi.RemoteException;
}
