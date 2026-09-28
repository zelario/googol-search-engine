package projetosd;

import java.rmi.Remote;
import java.util.Map;
import java.rmi.RemoteException;

/**
 * Remote interface for a distributed URL queue.
 */
public interface UrlQueueInterface extends Remote {
    /**
     * Retrieves and removes the next URL from the queue.
     * @param downloaderBarrels Downloader list to be updated for reliable multicast
     * @return The updated barrel list and the url removed
     * @throws RemoteException RMI Exception
     */
    Map<Map<Integer, BarrelInterface>, String> takeUrl(Map<Integer, BarrelInterface> downloaderBarrels) throws RemoteException;

    /**
     * Adds a URL to the queue. User input URLs are prioritized.
     * @param url The URL to add
     * @param userInput True if the URL was provided by the user, false otherwise
     * @throws RemoteException RMI Exception
     */
    void addUrl(String url, boolean userInput) throws RemoteException;

    /**
     * Ping queue to check connectivity
     * @param object Object to be used (can be null)
     * @throws RemoteException RMI Exception
     */
    void ping(Object object) throws RemoteException;

    /**
     * Changes barrel status to keep track of active barrels
     * @param barrels       Map of barrels with their statuses
     * @throws RemoteException RMI Exception
     */
    void updateBarrelList(Map<Integer, BarrelInterface> barrels) throws RemoteException;
}
