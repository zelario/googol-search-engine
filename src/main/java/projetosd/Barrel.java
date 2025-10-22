package projetosd;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of the Index barrel remote interface.
 * Handles indexing and searching of words across URLs.
 * 
 * @author Jose Amado e José Capinha
 * @version 1.0
 */
public class Barrel extends UnicastRemoteObject implements BarrelInterface {
    /**
     * Constructs the Barrel.
     * @throws RemoteException RMI exception
     */
    public Barrel() throws RemoteException {
        super();
    }

    /**
     * Pings the barrel to check if working.
     * @throws java.rmi.RemoteException RMI exception
     */
    @Override
    public void ping() throws java.rmi.RemoteException {
    }

    /**
     * Adds all necessary info into a barrel
     * @param url           Page URL
     * @param words         Words found in page
     * @param title         Page title
     * @param citation      Short citation from the page
     * @param relatedUrls   All urls in that page
     * @return              Boolean to indicate success or not
     */
    public static boolean addEntry(String url, ArrayList<String> words, String title, String citation, ArrayList<String> relatedUrls){
        Database db = new Database();

        String insertUrlQuery = "INSERT INTO url(url, title, citation) VALUES (?, ?, ?)";
        String insertPageUrlsQuery = "INSERT INTO url_url(url_url, url_url1) VALUES (?, ?)";
        String insertWordsQuery = "INSERT INTO words(word) VALUES (?) ON CONFLICT (word) DO NOTHING";
        String insertWordsUrlQuery = "INSERT INTO words_url(words_word, url_url) VALUES (?, ?)";

        // There is also a Connection object of jsoup so it is better to explicitly declare it as sql connction object
        try (java.sql.Connection conn = db.getConnection()){
            // Begin transaction
            conn.setAutoCommit(false);

            try (PreparedStatement psUrl = conn.prepareStatement(insertUrlQuery)) {
                psUrl.setString(1, url);
                psUrl.setString(2, title);
                psUrl.setString(3, citation);

                try (ResultSet rs = psUrl.executeQuery()) {
                    rs.next();
                }
            }

            if (relatedUrls != null && !relatedUrls.isEmpty()) {
                try (PreparedStatement psPageUrls = conn.prepareStatement(insertPageUrlsQuery)) {
                    for (String relatedUrl : relatedUrls) {
                        psPageUrls.setString(1, url);
                        psPageUrls.setString(2, relatedUrl);
                        psPageUrls.addBatch();
                    }

                    psPageUrls.executeBatch();
                }
            }

            if(words != null && !words.isEmpty()) {
                try (PreparedStatement psWords = conn.prepareStatement(insertWordsQuery)) {
                    for (String word : words) {
                        psWords.setString(1, word);
                        psWords.addBatch();
                    }

                    psWords.executeBatch();
                }

                try(PreparedStatement psWordsUrls = conn.prepareStatement(insertWordsUrlQuery)) {
                    for (String word : words) {
                        psWordsUrls.setString(1, word);
                        psWordsUrls.setString(2, url);
                        psWordsUrls.addBatch();
                    }

                    psWordsUrls.executeBatch();
                }
            }

            conn.commit();
            return true;

        } catch (Exception e){
            System.out.println("[DOWNLOADER] Error adding entry to barrels: " + e.getMessage());
            try { db.getConnection().rollback(); } catch (SQLException e1) {System.out.println("[DOWNLOADER] Barrel could not rollback" + e1.getMessage());}
            return false;
        }
    }

    /**
     *  Fetches from the barrel (DB) all the pages that contain all the terms in the search query
     * @param terms The search terms
     * @return      List of Page objects
     */
    @Override
    public List<Page> searchQuery(String[] terms) {
        Database db = new Database();
        List<Page> pages = new ArrayList<>();

        try (java.sql.Connection conn = db.getConnection()) {
            String placeholders = String.join(",", Collections.nCopies(terms.length, "?"));

            String query = "SELECT u.url, u.title, u.citation, COUNT(DISTINCT uu.url_url) AS ref_count " +
                    "FROM url u " +
                    "JOIN words_url wu ON wu.url_url = u.url " +
                    "JOIN words w ON wu.words_word = w.word " +
                    "LEFT JOIN url_url uu ON uu.url_url1 = u.url " +
                    "WHERE w.word IN (" + placeholders + ") " +
                    "GROUP BY u.url, u.title, u.citation " +
                    "HAVING COUNT(DISTINCT w.word) = ? " +
                    "ORDER BY ref_count DESC;";

            PreparedStatement stmt = conn.prepareStatement(query);

            for (int i = 0; i < terms.length; i++) {
                stmt.setString(i + 1, terms[i]);
            }

            stmt.setInt(terms.length + 1, terms.length);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    pages.add(new Page(rs.getString("url"), rs.getString("title"), rs.getString("citation")));
                }
            } catch (SQLException e) {
                System.out.println("[DOWNLOADER] Error fetching pages: " + e.getMessage());
            }
        } catch (Exception e) {
            System.out.println("[DOWNLOADER] Error fetching pages: " + e.getMessage());
        }

        return pages;
    }

    /**
     * Returns all pages that reference the given page
     * @param page Page that is referenced
     * @return     List of pages that referene the given page
     */
    public List<Page> getBacklinks(Page page){
        Database db = new Database();
        List<Page> pages = new ArrayList<>();

        try (java.sql.Connection conn = db.getConnection()){
            String query = "SELECT u.url " +
                    "FROM url u " +
                    "JOIN url_url uu ON uu.url_url = u.url " +
                    "WHERE uu.url_url1 = ?; ";

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, page.getUrl());

            try(ResultSet rs = stmt.executeQuery()){
                while (rs.next()) {
                    pages.add(new Page(rs.getString("url"), "", ""));
                }
            }
        }
        catch (Exception e){
            System.out.println("[DOWNLOADER] Error fetching pages: " + e.getMessage());
        }

        return pages;
    }

    /**
     * Main for Barrel. Starts the RMI registry and binds the barrel.
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        try {
            int port = Ports.claimBarrelPort();
            if (port == -1) {
                Debug.error("[BARREL] No available ports. All barrel ports are in use.");
                return;
            }

            Barrel barrel = new Barrel();
            Registry registry = LocateRegistry.createRegistry(port);
            registry.rebind("barrel", barrel);
            Debug.info("[BARREL] Running on port: " + port);

            GatewayInterface gateway;
            try {
                registry = LocateRegistry.getRegistry(Ports.GATEWAY_PORT);
                gateway = (GatewayInterface) registry.lookup("gateway");
                Debug.info("[BARREL] Connected to gateway on port " + Ports.GATEWAY_PORT);
                gateway.callbackBarrelStatus(port, true);
            } catch (NotBoundException | RemoteException e) {
                Debug.error("[BARREL] Gateway not available: " + e.getMessage());
            }

        } catch (RemoteException e) {
            Debug.error("[BARREL] Failed to start: " + e.getMessage());
        }
    }
}
