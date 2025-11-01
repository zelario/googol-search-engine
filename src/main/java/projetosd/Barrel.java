package projetosd;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private GatewayInterface gateway;

    /*
     * Port where this barrel is running
     */
    private int port;

    /**
     * Constructs the Barrel.
     * @throws RemoteException RMI exception
     */
    public Barrel() throws RemoteException {
        port = -1;
        this.port = Config.claimBarrelPort();
        if (this.port == -1) {
            Log.error("[BARREL] No available ports. All barrel ports are in use");
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
    @SuppressWarnings("BusyWait")
    public String addEntry(String url, ArrayList<String> words, String title, String citation, ArrayList<String> relatedUrls) throws RemoteException{
        Database db = new Database(this.port);

        // Because the downloader might insert urls that are in pages before they've been parsed...
        // Here we manage conflicts by updating the remaining info with the excluded insertion
        // Also every query has handling of conflicts because duplicates are common
        String insertUrlQuery = "INSERT INTO url(url, title, citation) VALUES (?, ?, ?) ON CONFLICT (url) DO UPDATE SET title = EXCLUDED.title, citation = EXCLUDED.citation, updated_at = now()";
        // Insert page urls before to avoid breaking foreign keys constraints
        String preInsertPageUrlsQuery = "INSERT INTO url(url) VALUES (?) ON CONFLICT (url) DO NOTHING";
        String insertPageUrlsQuery = "INSERT INTO url_url(url_url, url_url1) VALUES (?, ?) ON CONFLICT DO NOTHING";
        String insertWordsQuery = "INSERT INTO words(word) VALUES (?) ON CONFLICT (word) DO NOTHING";
        String insertWordsUrlQuery = "INSERT INTO words_url(words_word, url_url) VALUES (?, ?) ON CONFLICT DO NOTHING ";

        // deadlocks...
        int attempt = 0;
        while(attempt < Config.BARREL_RETRIES){
            // There is also a Connection object of jsoup so it is better to explicitly declare it as sql connection object
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
                reportIndexSize();
                return "ACK";

            } catch (SQLException e){
                String errorMsg = e.getMessage();
                if(errorMsg != null &&  errorMsg.contains("deadlock detected")){
                    attempt++;
                    Log.warning("[BARREL " + port + "] Deadlock detected on insertion");

                    try{ Thread.sleep((long) (Config.BARREL_BACKOFF * Math.pow(2, attempt)));}
                    catch (InterruptedException ignored){}
                }
                else {
                    String msg = e.getMessage();
                    // Ignore common race condition, fixed with sync
                    if(!(msg != null && msg.contains("violates foreign key constraint \"words_url_fk1\""))){
                        Log.error("[DOWNLOADER] Error adding entry to barrels: " + msg);
                    }
                    return "NACK";
                }
            }
    }
        Log.error("[BARREL " + port + "] Multiple deadlocks retries for url: " + url);
        return "NACK";
    }

    /**
     * Search for pages where url contains all terms.
     * @param rawQuery The raw user query
     * @param terms The search terms
     * @param pageNumber The page number for pagination (1-based)
     * @return Returns a list of pages (urls and metadata).
     */
    @Override
    public List<Page> searchQuery(String rawQuery, String[] terms, int pageNumber, int filter, String domain) throws RemoteException {
        long startTime = System.currentTimeMillis();

        if (terms == null || terms.length == 0) return Collections.emptyList();
        if (pageNumber < 1) pageNumber = 1;

        Database db = new Database(this.port);
        List<Page> pages = new ArrayList<>();

        try (java.sql.Connection conn = db.getConnection()) {
            String placeholders = String.join(",", Collections.nCopies(terms.length, "?"));

            StringBuilder filterQuery = new StringBuilder();
            filterQuery.append("WHERE w.word IN (").append(placeholders).append(") ");
            filterQuery.append("AND u.title != 'Page' ");
            filterQuery.append("AND u.title IS NOT NULL ");
            filterQuery.append("AND u.citation IS NOT NULL ");

            if (filter == 2 || filter == 4) { 
                filterQuery.append("AND u.url LIKE '%.").append(domain).append("%' ");
            }
            
            if (filter == 3 || filter == 4) { 
                filterQuery.append("AND u.title ~ '^[A-Za-zÀ-ÖØ-öø-ÿ0-9[:punct:] ]*$' ");
                filterQuery.append("AND u.citation ~ '^[A-Za-zÀ-ÖØ-öø-ÿ0-9[:punct:] ]*$' ");
            }

            //noinspection SqlShouldBeInGroupBy
            String query = "SELECT u.url, u.title, u.citation, COUNT(DISTINCT uu.url_url) AS ref_count " +
                "FROM url u " +
                "JOIN words_url wu ON wu.url_url = u.url " +
                "JOIN words w ON wu.words_word = w.word " +
                "LEFT JOIN url_url uu ON uu.url_url1 = u.url " +
                filterQuery +
                "GROUP BY u.url, u.title, u.citation " +
                "HAVING COUNT(DISTINCT w.word) = ? " +
                "ORDER BY ref_count DESC " +
                "LIMIT 10 OFFSET ?;";

            PreparedStatement stmt = conn.prepareStatement(query);

            // set terms
            for (int i = 0; i < terms.length; i++) {
                stmt.setString(i + 1, terms[i].toLowerCase());
            }

            // total terms count
            stmt.setInt(terms.length + 1, terms.length);

            // calculate offset for pagination
            int offset = (pageNumber - 1) * 10;
            stmt.setInt(terms.length + 2, offset);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    pages.add(new Page(
                            rs.getString("url"),
                            rs.getString("title"),
                            rs.getString("citation")
                    ));
                }
            } catch (SQLException e) {
                Log.error("[DOWNLOADER] Error fetching pages: " + e.getMessage());
            }
        } catch (Exception e) {
            Log.error("[DOWNLOADER] Error fetching pages: " + e.getMessage());
        }

        long responseTime = System.currentTimeMillis() - startTime;

        try {
            gateway.reportSearchStats(port, rawQuery, responseTime);
        } catch (RemoteException e) {
            Log.error("[BARREL " + port + "] Failed to report search stats to gateway: " + e.getMessage());
        }

        return pages;
    }

    /**
     * Returns all pages that reference the given page
     * @param page Page that is referenced
     * @return List of pages that reference the given page
     */
    @Override
    public List<Page> getBacklinks(Page page) throws RemoteException{
        Database db = new Database(this.port);
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
            Log.error("[BARREL " + this.port + "] Error fetching pages: " + e.getMessage());
        }

        return pages;
    }

    /**
     * Returns all words found in a page
     * @param page Given Page
     */
    @Override
    public void getWordsInPage(Page page) throws java.rmi.RemoteException{
        Database db = new Database(this.port);
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
            Log.error("[BARREL] Error fetching pages: " + e.getMessage());
        }

        page.InsertWordsFound(words);
    }

    @Override
    public Map<String, String> getMD5Hash(String tableName, Timestamp now) throws java.rmi.RemoteException{
        if(Objects.equals(tableName, "") || Objects.equals(tableName ," ")) return calcDataBaseMd5Hash(now);
        return calcRowMD5Hash(tableName, now);
    }

    /**
     * Helper method to get columns and the reference column to order queries
     * @param tableName Name of the table
     * @return          Table columns and the supposed column to order data
     */
    private String[] getColumnsAndOrder(String tableName){
        String[] colsAndOrder = new String[2];

        colsAndOrder[0] = switch (tableName) {
            case "url" -> "url, title, citation";
            case "words" -> "word";
            case "words_url" -> "words_word, url_url";
            case "url_url" -> "url_url, url_url1";
            default -> "*";
        };

        colsAndOrder[1] = switch (tableName) {
            case "url" -> "url";
            case "url_url" -> "url_url, url_url1";
            case "words" -> "word";
            case "words_url" -> "words_word, url_url";
            default -> "*";
        };

        return colsAndOrder;
    }

    /**
     * Method to calculate MD5 hash for each table to later verify db states
     * @return  Map with tables as keys as hashes as values
     */
    private Map<String, String> calcDataBaseMd5Hash(Timestamp now){
        Database db = new Database(this.port);
        Map<String, String> hashes = new HashMap<>();
        String[] tables = {"words", "url", "words_url", "url_url"};

        try(java.sql.Connection conn = db.getConnection()){
            for(String table : tables){
                String[] tableStuff = this.getColumnsAndOrder(table);
                String columns = tableStuff[0];
                String orderColumn = tableStuff[1];

                String extraColumn = table.equals("url") ? "updated_at" : "created_at";

                // This query computes the tables hash using nested aggregation (aggregate of aggregated values, in this case aggregate each row (tables))
                // and then aggregate all rows for the table, this is very efficient memory-wise
                String query = String.format("SELECT md5(string_agg(md5(row(%s)::text), '' ORDER BY %s)) AS table_hash FROM %s t WHERE %s < ?;", columns, orderColumn, table, extraColumn);

                try(PreparedStatement stmt = conn.prepareStatement(query)){
                    stmt.setTimestamp(1, now);
                    try(ResultSet rs = stmt.executeQuery()){
                        if (rs.next()) {
                            String tableHash = rs.getString("table_hash");
                            hashes.put(table, tableHash != null ? tableHash : "");
                        }
                    }
                }
                catch (SQLException e){
                    Log.error("[BARREL] Error calculating table hash: " + e.getMessage());
                }
            }
        }
        catch (SQLException e){
            Log.error("[BARREL] Error calculating db hashes: " + e.getMessage());
        }

        return hashes;
    }

    /**
     * Compute MD5 hash for each row of a given table
     * @param tableName Table to compute hashes
     * @param now       Timestamp to only sync data not added after sync call
     * @return          Hash map with md5 hash as key and row data combined in one string as value
     */
    private Map<String, String> calcRowMD5Hash(String tableName, Timestamp now){
        Database db = new Database(this.port);
        Map<String, String> hashes = new HashMap<>();

        String[] tableStuff = this.getColumnsAndOrder(tableName);
        String columns = tableStuff[0];
        String orderColumns = tableStuff[1];

        String extraColumn = tableName.equals("url") ? "updated_at" : "created_at";

        try(java.sql.Connection conn = db.getConnection()){
            String query = String.format("SELECT md5(row(%s)::text) AS row_hash, CONCAT_WS(E'\\001', %s) AS combined_columns FROM %s WHERE %s < ? ORDER BY %s", columns, columns, tableName, extraColumn, orderColumns);

            try(PreparedStatement stmt = conn.prepareStatement(query)){
                stmt.setTimestamp(1, now);
                try(ResultSet rs = stmt.executeQuery()){
                    while (rs.next()) {
                        hashes.put(rs.getString("row_hash"), rs.getString("combined_columns"));
                    }
                }
            }
            catch (SQLException e){
                Log.error("[BARREL] Error calculating table row hash: " + e.getMessage());
            }
        }
        catch (SQLException e){
            Log.error("[BARREL] Error calculating db row hashes: " + e.getMessage());
        }

        return hashes;
    }

    @Override
    @SuppressWarnings("SqlSourceToSinkFlow")
    public void insertMissingRows(String table, ArrayList<String> content) throws java.rmi.RemoteException{
        Database db = new Database(this.port);

        String columns = getColumnsAndOrder(table)[0];
        String conflictHandling = table.equals("url")
                ? "(url) DO UPDATE SET " +
                "updated_at = NOW(), " +
                "title = CASE WHEN url.title = 'Page' THEN EXCLUDED.title ELSE url.title END, " +
                "citation = CASE WHEN url.title = 'Page' THEN EXCLUDED.citation ELSE url.citation END"
                : "DO NOTHING";
        try(java.sql.Connection conn = db.getConnection()){
            conn.setAutoCommit(false);

            int numColumns = columns.split(",", -1).length;
            String placeholders = String.join(",", Collections.nCopies(numColumns, "?"));
            String query = String.format("INSERT INTO %s (%s) VALUES (%s) ON CONFLICT %s", table, columns, placeholders, conflictHandling);

            try(PreparedStatement stmt = conn.prepareStatement(query)){
                for (String rawData: content){
                    String[] values = rawData.split("\u0001", -1);

                    for (int i = 0; i < values.length; i++) {
                        stmt.setString(i + 1, values[i]);
                    }

                    stmt.addBatch();
                }

                stmt.executeBatch();
            }

            conn.commit();
        }
        catch (SQLException e){
            //Log.error("[BARREL] Could not insert missing data in barrel: " + e.getMessage());  TODO
        }

        Log.info("[BARREL] Inserted missing data from sync");
    }

    /**
     * Fetches the current index size and reports it to the gateway.
     */
    private void reportIndexSize(){
        Database db = new Database(this.port);
        String query = "SELECT COUNT(*) AS index_size FROM words_url;";

        try (java.sql.Connection conn = db.getConnection()){
            PreparedStatement stmt = conn.prepareStatement(query);

            try (ResultSet rs = stmt.executeQuery()){
                if(rs.next()){
                    int indexSize = rs.getInt("index_size");

                    try {
                        gateway.reportIndexStats(port, indexSize);
                    } catch (RemoteException e) {
                        Log.error("[BARREL " + port + "] Failed to report index stats to gateway: " + e.getMessage());
                    }
                }
            }
        }
        catch (SQLException e){
            Log.error("[BARREL] Error fetching index size: " + e.getMessage());
        }
    }

    /**
     * Main for Barrel. Starts the RMI registry and binds the barrel.
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        try {
            Barrel barrel = new Barrel();

            Registry registry = LocateRegistry.createRegistry(barrel.port);
            registry.rebind("barrel", barrel);
            Log.info("[BARREL " + barrel.port + "] Running on " + Config.BARREL_HOSTS_TRANSLATION_TABLE.get(barrel.port) + ":" + barrel.port);

            registry = LocateRegistry.getRegistry(Config.GATEWAY_HOST, Config.GATEWAY_PORT);
            barrel.gateway = (GatewayInterface) registry.lookup("gateway");

            Log.info("[BARREL " + barrel.port + "] Requesting sync to gateway");
            if(barrel.gateway.syncBarrels(barrel.port)){
                Log.info("[BARREL " + barrel.port + "] Sync successful");
            }

            barrel.gateway.reportBarrelStatus(barrel.port, true);
            Log.info("[BARREL " + barrel.port + "] Registered with gateway on " + Config.GATEWAY_HOST + ":" + Config.GATEWAY_PORT);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (barrel.gateway == null) {
                        Registry reg = LocateRegistry.getRegistry(Config.GATEWAY_HOST, Config.GATEWAY_PORT);
                        barrel.gateway = (GatewayInterface) reg.lookup("gateway");
                    }
                    barrel.gateway.reportBarrelStatus(barrel.port, false);
                    Log.info("[BARREL " + barrel.port + "] Shutdown notification sent to gateway. Exiting.");
                } catch (NotBoundException | RemoteException e) {
                    Log.error("[BARREL " + barrel.port + "] Failed to notify gateway on shutdown: " + e.getMessage());
                }
            }));

        } catch (RemoteException | NotBoundException e) {
            Log.error("[BARREL] Failed to start Barrel RMI Server: " + e.getMessage());
            System.exit(1);
        }
    }
}
