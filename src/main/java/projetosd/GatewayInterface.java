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
    /**
     * Submit a URL to be indexed.
     * @param url URL to index
     * @throws RemoteException
     */
    void index(String url) throws RemoteException;

    /**
    * Search for a query.
    * @param query Search query
    * @return list of pages
    * @throws RemoteException 
    */
    List<List<Page>> search(String query) throws RemoteException;

    /**
     * Get simple stats string from a barrel.
     * @return stats string
     * @throws RemoteException 
     */
    String stats() throws RemoteException;
}

