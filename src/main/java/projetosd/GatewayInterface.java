package projetosd;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;

/**
 * Remote interface for the Gateway for clients.
 * 
 * @author Jose Amado e José Capinha
 * @version 1.0
 */
public interface GatewayInterface extends Remote {
    /*
     * CALLBACK: Notifies the Gateway to update statistics from a Barrel.
     * @param barrelPort Barrel port
     * @param indexSize Size of Barrel index
     * @param urlsParsed Number of URLs processed
     */
    void callbackStats(int barrelPort, int indexSize, long urlsParsed) throws RemoteException;

    /**
     * CALLBACK: Notifies the Gateway about a Barrel's status change.
     * @param barrelPort Barrel port
     * @param status Status message
     * @throws RemoteException
     */
    void callbackBarrelStatus(int barrelPort, boolean status) throws RemoteException;

    /**
     * CALLBACK: Notifies the Gateway that a search has been completed by a Barrel.
     * @param barrelPort Barrel port
     * @param queryId Query identifier
     * @param responseTime Response time in ms
     * @throws RemoteException
     */
    void callbackSearchCompleted(int barrelPort, String queryId, long responseTime) throws RemoteException;

    /**
     * Submit a URL to be indexed.
     * @param url URL to index
     * @throws RemoteException
     */
    void index(String url) throws RemoteException;

    /**
    * Search for a query.
    * @param query Search query
    * @param clientId Client identifier
    * @return list of pages
    * @throws RemoteException 
    */
    List<List<Page>> search(String clientId, String query) throws RemoteException;

    /**
     * Get simple stats string from a barrel.
     * @param clientId Client identifier
     * @return stats string
     * @throws RemoteException 
     */
    String stats(String clientId) throws RemoteException;
}

