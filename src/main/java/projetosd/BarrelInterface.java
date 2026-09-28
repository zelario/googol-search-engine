package projetosd;

import java.rmi.Remote;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.rmi.RemoteException;

/**
 * Remote interface for the distributed index server.
 */
public interface BarrelInterface extends Remote {

    /**
     * Search for pages where url contains all terms.
     * @param rawQuery The raw user query
     * @param terms The search terms
     * @param pageNumber The page number for pagination (1-based)
     * @param filter Filter type
     * @param domain Domain to be filtered
     * @return Returns a list of pages (urls and metadata).
     * @throws RemoteException RMI Exception
     */
    List<Page> searchQuery(String rawQuery, String[] terms, int pageNumber, int filter, String domain) throws RemoteException;

    /**
     * Pings the barrel to check if working.
     * @param object Object to be used (can be null)
     * @throws RemoteException if a remote error occurs
     */
    void ping(Object object) throws RemoteException;

    /**
     * Adds all necessary info into a barrel
     * @param url           Page URL
     * @param words         Words found in page
     * @param title         Page title
     * @param citation      Short citation from the page
     * @param relatedUrls   All urls in that page
     * @return              "ACK" on success, "NACK" otherwise
     * @throws RemoteException RMI Exception
     */
    String addEntry(String url, ArrayList<String> words, String title, String citation, ArrayList<String> relatedUrls) throws RemoteException;

    /**
     * Returns all pages that reference the given page
     * @param page Page that is referenced
     * @return     List of pages that reference the given page
     * @throws RemoteException RMI Exception
     */
    List<Page> getBacklinks(Page page) throws RemoteException;

    /**
     * RMI Method to calculate MD5 hash. If a table name is passed it computes the table hash per row, if not it computes for each table
     * @param tableName Name of the table to get hash
     * @param now Current time for sync
     * @return  Map with tables as keys as hashes as values
     * @throws RemoteException RMI Exception
     */
    Map<String, String> getMD5Hash(String tableName, Timestamp now) throws RemoteException;

    /**
     * Method called by the gateway to insert missing info from other barrels
     * @param table     Table to insert content into
     * @param content   Missing data from the db
     * @throws RemoteException RMI Exception
     */
    void insertMissingRows(String table, ArrayList<String> content) throws RemoteException;

    /**
     * Checks and updates stop words in the barrel database.
     * @throws RemoteException RMI Exception
     */
    public void checkStopWords() throws RemoteException;
}
