package projetosd;

import java.rmi.Remote;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Remote interface for the distributed index server.
 * 
 * @author Jose Amado & José Capinha
 * @version 1.0
 */
public interface BarrelInterface extends Remote {
    /**
     * Search for pages where url contains all terms.
     * @param rawQuery The raw user query
     * @param terms The search terms
     * @param pageNumber The page number for pagination (1-based)
     * @return Returns a list of pages (urls and metadata).
     */
    List<Page> searchQuery(String rawQuery, String[] terms, int pageNumber) throws java.rmi.RemoteException;

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
    boolean addEntry(String url, ArrayList<String> words, String title, String citation, ArrayList<String> relatedUrls) throws java.rmi.RemoteException;

    /**
     * Returns all pages that reference the given page
     * @param page Page that is referenced
     * @return     List of pages that referene the given page
     */
    List<Page> getBacklinks(Page page) throws java.rmi.RemoteException;

    /**
     *  Returns all words found in a page
     * @param page Given Page
     * @return List of words related to the given page (url)
     */
    List<String> getWordsInPage(Page page) throws java.rmi.RemoteException;

    /**
     * RMI Method to calculate MD5 hash. If a table name is passed it computes the table hash per row, if not it computes for each table
     * @return  Map with tables as keys as hashes as values
     */
    Map<String, String> getMD5Hash(String tableName, Timestamp now) throws java.rmi.RemoteException;

    /**
     * Method called by the gateway to insert missing info from other barrels
     * @param table     Table to insert content into
     * @param content   Missing data from the db
     * @throws java.rmi.RemoteException RMI Exception
     */
    void insertMissingRows(String table, Collection<String> content) throws java.rmi.RemoteException;
}
