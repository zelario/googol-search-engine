package projetosd;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;

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
    List<Page> search(String clientId, String query, int pageNumber) throws RemoteException;

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

    /**
     * Method that asynchronously checks and handles database synchronization
     * @returns Boolean (always true) to block barrel execution so they complete the sync before registering as active
     * @throws RemoteException RMI Exception
     */
    boolean synchBarrels(int requesterPort) throws RemoteException;

    /**
     * Gateway method to multicast the data, that came from the downloader, into all active barrels
     * @param url           Page url
     * @param words         Words in page
     * @param title         Page title
     * @param citation      Short descripion/citation from the page
     * @param relatedUrls   Urls found in the page
     * @return              Boolean that if true multicast worked, if false no info was introduced in any Barrel (DB) so downloaders must re-insert url into queue
     * @throws RemoteException  RMI Exception
     */
    boolean multicastEntries(String url, ArrayList<String> words, String title, String citation, ArrayList<String> relatedUrls) throws RemoteException;
}

