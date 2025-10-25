package projetosd;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementation of the Index barrel remote interface.
 * Handles indexing and searching of words across URLs.
 * 
 * @author Jose Amado & José Capinha
 * @version 1.0
 */
public class Barrel extends UnicastRemoteObject implements BarrelInterface {

    /*
     * Reference to the Gateway for callbacks
     */
    private GatewayInterface gateway = null;

    /*
     * Port where this barrel is running
     */
    private int port = -1;

    /**
     * Constructs the Barrel.
     * @throws RemoteException RMI exception
     */
    public Barrel() throws RemoteException {
        port = Config.claimBarrelPort();
        if (port == -1) {
            Log.error("[BARREL] No available ports. All barrel ports are in use.");
        }

        try {
            Registry registry = LocateRegistry.getRegistry(Config.GATEWAY_PORT);
            gateway = (GatewayInterface) registry.lookup("gateway");
            Log.info("[BARREL " + port + "] Connected to gateway on port: " + Config.GATEWAY_PORT);
            gateway.callbackBarrelStatus(port, true);
        } catch (NotBoundException | RemoteException e) {
            Log.error("[BARREL " + port + "] Gateway not available: " + e.getMessage());
        }
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
    @Override
    public boolean addEntry(String url, ArrayList<String> words, String title, String citation, ArrayList<String> relatedUrls){
        Database db = new Database();

        // Because the downloader might insert urls that are in pages before they've been parsed...
        // Here we manage conflicts by updating the remaining info with the excluded insertion
        // Also every query has handling of conflicts because duplicates are common
        String insertUrlQuery = "INSERT INTO url(url, title, citation) VALUES (?, ?, ?) ON CONFLICT (url) DO UPDATE SET title = EXCLUDED.title, citation = EXCLUDED.citation";
        // Insert page urls before to avoid breaking foreign keys constraints
        String preInsertPageUrlsQuery = "INSERT INTO url(url) VALUES (?) ON CONFLICT (url) DO NOTHING";
        String insertPageUrlsQuery = "INSERT INTO url_url(url_url, url_url1) VALUES (?, ?) ON CONFLICT DO NOTHING";
        String insertWordsQuery = "INSERT INTO words(word) VALUES (?) ON CONFLICT (word) DO NOTHING";
        String insertWordsUrlQuery = "INSERT INTO words_url(words_word, url_url) VALUES (?, ?) ON CONFLICT DO NOTHING ";

        // deadlocks...
        int attempt = 0;
        while(attempt < 3){
            // There is also a Connection object of jsoup so it is better to explicitly declare it as sql connction object
            try (Connection conn = db.getConnection()){
                // Begin transaction (if it fails jdbc rollbacks automatically)
                conn.setAutoCommit(false);
                // Set transactions to READ_COMMITED (default apparently but here anyway to make sure, some drivers can overlap)
                conn.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);

                try (PreparedStatement psUrl = conn.prepareStatement(insertUrlQuery)) {
                    psUrl.setString(1, url);
                    psUrl.setString(2, title);
                    psUrl.setString(3, citation);

                    psUrl.executeUpdate();
                }
                conn.commit();

                // noinspection  DuplicatedCode
                if (relatedUrls != null && !relatedUrls.isEmpty()) {
                    try(PreparedStatement psPrePageUrls = conn.prepareStatement(preInsertPageUrlsQuery)){
                        // Insert before to keep integrity
                        for(String relurl: relatedUrls){
                            psPrePageUrls.setString(1, relurl);

                            psPrePageUrls.addBatch();
                        }
                        psPrePageUrls.executeBatch();
                        conn.commit();
                    }


                    try (PreparedStatement psPageUrls = conn.prepareStatement(insertPageUrlsQuery)) {
                        for (String relatedUrl : relatedUrls) {
                            psPageUrls.setString(1, url);
                            psPageUrls.setString(2, relatedUrl);
                            psPageUrls.addBatch();
                        }

                        psPageUrls.executeBatch();
                        conn.commit();
                    }
                }

                // noinspection  DuplicatedCode
                if(words != null && !words.isEmpty()) {
                    try (PreparedStatement psWords = conn.prepareStatement(insertWordsQuery)) {
                        for (String word : words) {
                            psWords.setString(1, word);
                            psWords.addBatch();
                        }

                        psWords.executeBatch();
                        conn.commit();
                    }

                    try(PreparedStatement psWordsUrls = conn.prepareStatement(insertWordsUrlQuery)) {
                        for (String word : words) {
                            psWordsUrls.setString(1, word);
                            psWordsUrls.setString(2, url);
                            psWordsUrls.addBatch();
                        }

                        psWordsUrls.executeBatch();
                        conn.commit();
                    }
                }

                Log.url("[BARREL " + port + "] Inserted url into database: " + url);
                return true;

            } catch (SQLException e){
                String errorMsg = e.getMessage();
                if(errorMsg != null &&  errorMsg.contains("deadlock detected")){
                    attempt++;
                    Log.warning("[BARREL " + port + "] Deadlock detected on insertion");

                    try{ Thread.sleep((long) (100 * Math.pow(2, attempt)));}
                    catch (InterruptedException ignored){}
                }
                else {
                    Log.error("[DOWNLOADER] Error adding entry to barrels: " + e.getMessage());
                    return  false;
                }
            }
    }

        Log.error("[BARREL " + port + "] Multiple deadlocks retries for url: " + url);
        return false;
    }

    /**
     * Search for pages where url contains all terms.
     * @param terms The search terms
     * @return Returns a list of pages (urls and metadata).
     */
    @Override
    public List<Page> searchQuery(String rawQuery, String[] terms) {
        long startTime = System.currentTimeMillis();

        if(terms == null || terms.length == 0) return Collections.emptyList();

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
                stmt.setString(i + 1, terms[i].toLowerCase());
            }

            stmt.setInt(terms.length + 1, terms.length);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    pages.add(new Page(rs.getString("url"), rs.getString("title"), rs.getString("citation")));
                }
            } catch (SQLException e) {
                Log.error("[DOWNLOADER] Error fetching pages: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.error("[DOWNLOADER] Error fetching pages: " + e.getMessage());
        }

        long responseTime = System.currentTimeMillis() - startTime;

        try {
            gateway.callbackSearchCompleted(port, rawQuery, responseTime);
        } catch (RemoteException e) {
            Log.error("[BARREL " + port + "] Failed to callback gateway: " + e.getMessage());
        }

        return pages;
    }

    /**
     * Returns all pages that reference the given page
     * @param page Page that is referenced
     * @return List of pages that reference the given page
     */
    @Override
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
            Log.error("[DOWNLOADER] Error fetching pages: " + e.getMessage());
        }

        return pages;
    }

    /**
     *  Returns all words found in a page
     * @param page Given Page
     * @return List of words related to the given page (url)
     */
    public List<String> getWordsInPage(Page page){
        Database db = new Database();
        List<String> words = new ArrayList<>();

        try (java.sql.Connection conn = db.getConnection()){
            String query = "SELECT wu.words_word " +
                    "FROM words_url wu " +
                    "WHERE wu.url_url = ?; ";

            PreparedStatement stmt = conn.prepareStatement(query);
            stmt.setString(1, page.getUrl());

            try(ResultSet rs = stmt.executeQuery()){
                while (rs.next()) {
                    words.add(rs.getString("words_word"));
                }
            }
        }
        catch (Exception e){
            Log.error("[DOWNLOADER] Error fetching pages: " + e.getMessage());
        }

        return words;
    }

    /**
     * Method to calculate MD5 hash for each table to later verify db states
     * @return  Map with tables as keys as hashes as values
     */
    public Map<String, String> calcDataBaseMd5Hash(){
        Database db = new Database();
        Map<String, String> hashes = new HashMap<>();
        String[] tables = {"words", "url", "words_url", "url_url"};

        try(java.sql.Connection conn = db.getConnection()){
            for(String table : tables){
                String query = String.format("SELECT md5(row(t.*)::text) AS row_hash FROM %s t ORDER BY t.id;", table);

                try(PreparedStatement stmt = conn.prepareStatement(query)){
                    // Fetch 1000 rows at a time instead of all for efficiency
                    stmt.setFetchSize(1000);

                    MessageDigest md = MessageDigest.getInstance("MD5");
                    try(ResultSet rs = stmt.executeQuery()){
                        while (rs.next()) {
                            String rowHash = rs.getString("row_hash");
                            if (rowHash != null) {
                                md.update(rowHash.getBytes(StandardCharsets.UTF_8), 0, rowHash.length());
                            }
                        }

                        // Convert byte array into a hex string
                        byte[] digest = md.digest();
                        StringBuilder sb = new StringBuilder();
                        for (byte b : digest) {
                            sb.append(String.format("%02x", b));
                        }
                        String tableHash = sb.toString();

                        hashes.put(table, tableHash);
                    }
                }
                catch (SQLException e){
                    Log.error("[BARREL] Error calculating table hash: " + e.getMessage());
                }
                catch (NoSuchAlgorithmException e) {
                    Log.error("[BARREL] Error computing hashes: " + e.getMessage());
                }
            }
        }
        catch (SQLException e){
            Log.error("[BARREL] Error calculating db hashes: " + e.getMessage());
        }

        return hashes;
    }

    /**
     * Main for Barrel. Starts the RMI registry and binds the barrel.
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        try {
            Barrel barrel = new Barrel();

            if (barrel.port == -1) {
                Log.error("[BARREL] Exiting due to lack of available ports.");
                return;
            }

            Registry registry = LocateRegistry.createRegistry(barrel.port);
            registry.rebind("barrel", barrel);
            Log.info("[BARREL " + barrel.port + "] Running on port: " + barrel.port);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (barrel.gateway == null) {
                        Registry reg = LocateRegistry.getRegistry(Config.GATEWAY_PORT);
                        barrel.gateway = (GatewayInterface) reg.lookup("gateway");
                    }
                    barrel.gateway.callbackBarrelStatus(barrel.port, false);
                    Log.info("[BARREL " + barrel.port + "] Shutdown notification sent to gateway. Exiting.");
                } catch (NotBoundException | RemoteException e) {
                    Log.error("[BARREL " + barrel.port + "] Failed to notify gateway on shutdown: " + e.getMessage());
                }
            }));

        } catch (RemoteException e) {
            Log.error("[BARREL] Failed to start: " + e.getMessage());
        }
    }
}
