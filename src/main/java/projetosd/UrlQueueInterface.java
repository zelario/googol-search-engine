package projetosd;

import java.rmi.Remote;
import java.util.Map;

/**
 * Remote interface for a distributed URL queue.
 * 
 * @author Jose Amado & José Capinha
 * @version 1.0
 */
public interface UrlQueueInterface extends Remote {
    /**
     * Retrieves and removes the next URL from the queue.
     * @param downloaderBarrels Downloader list to be updated for reliable multicast
     * @return The updated barrel list and the url removed
     * @throws java.rmi.RemoteException RMI Exception
     */
    Map<Map<Integer, BarrelInterface>, String> takeUrl(Map<Integer, BarrelInterface> downloaderBarrels) throws java.rmi.RemoteException;

    /**
     * Adds a URL to the queue. User input URLs are prioritized.
     * @param url The URL to add
     * @param userInput True if the URL was provided by the user, false otherwise
     * @throws java.rmi.RemoteException RMI Exception
     */
    void addUrl(String url, boolean userInput) throws java.rmi.RemoteException;

    /**
     * Changes barrel status to keep track of active barrels
     * @param barrelPort    Barrel port
     * @param status        New status
     * @throws java.rmi.RemoteException RMI Exception
     */
    void changeBarrelStatus(int barrelPort, boolean status) throws java.rmi.RemoteException;
}
