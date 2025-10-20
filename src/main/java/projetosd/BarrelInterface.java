package projetosd;

import java.rmi.Remote;
import java.util.List;

/**
 * Remote interface for the distributed index server.
 * 
 * @author Jose Amado e José Capinha
 * @version 1.0
 */
public interface BarrelInterface extends Remote {
    /**
     * Search for pages where url contains all terms.
     * @param terms The search terms
     * @returns Returns a list of pages (urls and metadata).
     */
    List<Page> searchQuery(String[] terms) throws java.rmi.RemoteException;

    /**
     * Pings the barrel to check if working.
     * @throws java.rmi.RemoteException if a remote error occurs
     */
    void ping() throws java.rmi.RemoteException;
}
