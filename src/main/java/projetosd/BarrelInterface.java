package projetosd;

import java.rmi.Remote;
import java.util.ArrayList;
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
     * @return Returns a list of pages (urls and metadata).
     */
    List<Page> searchQuery(String[] terms) throws java.rmi.RemoteException;

    /**
     * Pings the barrel to check if working.
     * @throws java.rmi.RemoteException if a remote error occurs
     */
    void ping() throws java.rmi.RemoteException;

    /**
     * Adds all necessary info into a barrel
     * @param url           Page URL
     * @param words         Words found in page
     * @param title         Page title
     * @param citation      Short citation from the page
     * @param relatedUrls   All urls in that page
     * @return              Boolean to indicate success or not
     */
    public boolean addEntry(String url, ArrayList<String> words, String title, String citation, ArrayList<String> relatedUrls) throws java.rmi.RemoteException;

    /**
     * Returns all pages that reference the given page
     * @param page Page that is referenced
     * @return     List of pages that referene the given page
     */
    public List<Page> getBacklinks(Page page) throws java.rmi.RemoteException;

}
