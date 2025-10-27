package projetosd;

import java.rmi.Remote;
import java.rmi.RemoteException;

/**
 * Remote interface for the Gateway for clients.
 * 
 * @author Jose Amado & José Capinha
 * @version 1.0
 */
public interface GatewayInterface extends Remote {

    /**
     * CALLBACK: Notifies the Gateway about a Barrel's status change.
     * @param barrelPort Barrel port
     * @param status Status message
     * @throws RemoteException RMI Exception
     */
    void reportBarrelStatus(int barrelPort, boolean status) throws RemoteException;

    /*
     * CALLBACK: Notifies the Gateway to update statistics from a Barrel.
     * @param barrelPort Barrel port
     * @param indexSize Size of Barrel index
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
    * @param query Search query
    * @param clientId Client identifier
    * @return formatted search results string
    * @throws RemoteException RMI Exception
    */
    String search(String clientId, String query, int pageNumber) throws RemoteException;

    /**
     * Get simple stats string from a barrel.
     * @param clientId Client identifier
     * @return stats string
     * @throws RemoteException RMI Exception
     */
    String stats(String clientId) throws RemoteException;

    /**
     * Method that checks and handles database synchronization
     * @throws RemoteException RMI Exception
     */
    void synchBarrels() throws RemoteException;

}

