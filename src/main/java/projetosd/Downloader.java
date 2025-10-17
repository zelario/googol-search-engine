package projetosd;

import java.rmi.registry.LocateRegistry;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Downloader for fetching and processing web pages.
 * Handles downloading, parsing, indexing, and queueing new URLs.
 * 
 * @author Jose Amado e José Capinha
 * @version 1.0
 */
public class Downloader extends Thread {

    /**
     * The thread number for this downloader instance.
     */
    private final int threadNum;

    /**
     * Constructs a Downloader.
     * @param threadNum The thread number
     */
    public Downloader(int threadNum) {
        this.threadNum = threadNum;
    }

    /**
     * Main for Downloader. Starts multiple Downloader threads.
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        // CHANGE THREAD NUMBER HERE
        int threadCounter = 3;



        for (int i = 0; i < threadCounter; i++) {
            new Downloader(i + 1).start();
        }
    }

    /**
     * Work for the downloader thread. Fetches URLs, parses content, updates index, and adds new links to the queue.
     */
    @Override
    public void run() {
        try {
            UrlQueueInterface queue = (UrlQueueInterface) LocateRegistry.getRegistry(1099).lookup("queue");
            ArrayList<String> pageWords = new ArrayList<>();
            ArrayList<String> relatedUrls = new ArrayList<>();

            while (true) {
                String url = queue.takeUrl();

                System.out.println(threadNum + ": " + url);
                Document doc;
                try {
                    doc = Jsoup.connect(url).get();
                    //System.out.println(doc);
                } catch (HttpStatusException e) {
                    continue;
                }

                String text = doc.body().text();
                StringTokenizer st = new StringTokenizer(text, " \t\n\r\f,.:;?![]'\"");

                while (st.hasMoreTokens()) {
                    pageWords.add(st.nextToken());
                }

                Elements links = doc.select("a[href]");

                for (Element link : links) {
                    String pageUrl = link.attr("href");
                    if ((pageUrl.startsWith("https://"))) {
                        queue.addUrl(pageUrl, false);
                        relatedUrls.add(pageUrl);
                    }
                }

                // Fetch title and description
                String title = doc.title();
                String description = "";

                // Try different descriptions
                String metaDesc = doc.select("meta[name=description]").attr("content");
                if(!metaDesc.isBlank()) description = truncateDescription(metaDesc);

                Elements paragraphs = doc.select("p");
                for (Element p : paragraphs){
                    String paraText = p.text().trim();
                    // Ignore very short descriptions
                    if (text.length() > 15) description = truncateDescription(paraText);
                }

                String bodyText = doc.body().text();
                description = truncateDescription(bodyText);

                // TODO: add failback logic
                if(!addEntry(url, pageWords, title, description, relatedUrls)){
                    System.out.println("[DOWNLOADER] Failed to parse and store an url");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     *  Adds all necessary info into a barrel
     * @param url           Page URL
     * @param words         Words found in page
     * @param title         Page title
     * @param citation      Short citation from the page
     * @param relatedUrls   All urls in that page
     * @return              Boolean to indicate success or not
     * @throws SQLException DB (barrel) Error
     */
    private boolean addEntry(String url, ArrayList<String> words, String title, String citation, ArrayList<String> relatedUrls) throws SQLException {
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
                        psWords.setString(1, url);
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

    private static String truncateDescription(String description){
        int maxLength = 30;

        if (description.length() < maxLength) return description;

        int periodIndex = description.indexOf(".", maxLength);
        if(periodIndex != -1) return description.substring(0, periodIndex + 1).trim();

        return description.substring(0, maxLength).trim() + "...";
    }
}
