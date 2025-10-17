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
     * @return true if accepted
     */
    boolean addUrl(String url) throws RemoteException;

    /**
     * Search for a single term..
     * @param term search term
     * @param page 1-based page number
     * @return list of URLs (may be fewer than 10)
     */
    List<String> search(String term, int page) throws RemoteException;

    /**
     * Get simple stats string from a barrel (gateway may forward).
     */
    String getStats() throws RemoteException;

}

