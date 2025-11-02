package projetosd;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import java.util.Map;

/**
 * Remote interface for the Gateway for clients.
 */
public interface GatewayInterface extends Remote {

    /**
     * Callback: URL Queue notifies state changes.
     * @param status true if active, false if inactive
     * @throws RemoteException RMI exception
     */
    void reportQueueStatus(boolean status) throws RemoteException;

    /**
     * CALLBACK: Notifies the Gateway about a Barrel's status change.
     * @param barrelPort Barrel port
    *  @param status true if active, false if inactive
     * @throws RemoteException RMI Exception
     */
    void reportBarrelStatus(int barrelPort, boolean status) throws RemoteException;

    /**
     * Get map of active barrels
     * @return  Map of active barrels
     * @throws RemoteException RMI Exception
     */
    Map<Integer, BarrelInterface> getActiveBarrels() throws RemoteException;

    /**
     * Method that asynchronously checks and handles database synchronization
     * @param requesterPort Port of the requesting barrel
     * @return Boolean (always true) to block barrel execution so they complete the sync before registering as active
     * @throws RemoteException RMI Exception
     */
    boolean syncBarrels(int requesterPort) throws RemoteException;

    /**
     * CALLBACK: Notifies the Gateway to update statistics from a Barrel.
     * @param barrelPort Barrel port
     * @param indexSize Size of Barrel index
     * @throws RemoteException RMI Exception
     */
    void reportIndexStats(int barrelPort, int indexSize) throws RemoteException;

    /**
     * CALLBACK: Notifies the Gateway that a search has been completed by a Barrel.
     * @param barrelPort Barrel port
     * @param queryId Query identifier
     * @param responseTime Response time in ms
     * @throws RemoteException RMI Exception
     */
    void reportSearchStats(int barrelPort, String queryId, long responseTime) throws RemoteException;

    /**
     * Submit a URL to be indexed.
     * @param url URL to index
     * @throws RemoteException RMI Exception
     */
    void index(String url) throws RemoteException;

    /**
    * Search for a query.
    * @param clientId   Client identifier
    * @param query      Search query
    * @param pageNumber Current page being looked at
    * @param filter     Filter to be used
    * @param domain     Domain constraint when filter requires it
    * @return list of pages for the requested page number (up to 10)
    * @throws RemoteException RMI Exception
    */
    List<Page> search(String clientId, String query, int pageNumber, int filter, String domain) throws RemoteException;

    /**
     * Get the backlinks for a specific page.
     * @param clientId Client identifier
     * @param page Page object
     * @return list of backlinks
     * @throws RemoteException RMI Exception
     */
    List<Page> backlinks(String clientId, Page page) throws RemoteException;

    /**
     * Get simple stats string from a barrel.
     * @param clientId Client identifier
     * @return stats string
     * @throws RemoteException RMI Exception
     */
    String stats(String clientId) throws RemoteException;
}

