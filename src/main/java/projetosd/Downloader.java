package projetosd;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Downloader for fetching and processing web pages.
 * Handles downloading, parsing, indexing, and queueing new URLs.
 * 
 * @author Jose Amado & José Capinha
 * @version 1.0
 */
public class Downloader extends Thread {

    /**
     * The thread number for this downloader instance.
     */
    private final int threadNumber;

    /**
     * Regex pattern to validate parsed words
     */
    private static final Pattern VALID_WORDS = Pattern.compile("^\\p{L}[\\p{L}\\p{M}\\p{Pd}'’]{1,63}$");

    /**
     * Constructs a Downloader.
     * @param threadNum The thread number
     */
    public Downloader(int threadNum) {
        this.threadNumber = threadNum;
    }

    // Current connected barrel and its port
    private BarrelInterface barrel = null;
    private int barrelPort = -1;

    /**
     * Try to find any available barrel from configured ports and set the `barrel` and `barrelPort` fields.
     * @return found barrel port or -1 if none found
     */
    private void connectBarrel() {
        for (int port : Config.BARREL_PORTS) {
            try {
                BarrelInterface b = (BarrelInterface) LocateRegistry.getRegistry(port).lookup("barrel");
                b.ping();
                this.barrel = b;
                this.barrelPort = port;
            } catch (NotBoundException | RemoteException ignored) {}
        }
    }

    /**
     * Attempt to reconnect to any barrel with retries and backoff.
     * @return true if reconnected, false otherwise
     */
    @SuppressWarnings("SleepWhileInLoop")
    private boolean attemptReconnect() {
        for (int attempt = 1; attempt <= Config.DOWNLOADER_RETRIES; attempt++) {
            Log.info("[DOWNLOADER " + threadNumber + "] Attempt " + attempt + " to reconnect to a barrel.");
            connectBarrel();
            if (barrelPort != -1) {
                Log.info("[DOWNLOADER " + threadNumber + "] Reconnected to Barrel on port " + barrelPort + " (attempt " + attempt + ")");
                return true;
            }

            try {
                Thread.sleep((long) (Config.DOWNLOADER_BACKOFF * Math.pow(2, attempt - 1)));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return false;
    }

    /**
     *  Truncates description to reduce citation size
     *
     * @param description   Text to be truncated or not
     * @return              (If necessary) Trimmed text
     */
    private static String truncateDescription(String description){
        int maxLength = 60;

        if (description.length() < maxLength) return description;

        int periodIndex = description.indexOf(".");
        if(periodIndex != -1 && periodIndex <= maxLength) return description.substring(0, periodIndex + 1).trim();

        return description.substring(0, maxLength).trim() + "...";
    }

    /**
     * Work for the downloader thread. Fetches URLs, parses content, updates index, and adds new links to the queue.
     */
    @Override
    public void run() {
        try {
            Log.info("[DOWNLOADER " + threadNumber + "] Starting downloader thread.");
            UrlQueueInterface queue = (UrlQueueInterface) LocateRegistry.getRegistry(Config.URL_QUEUE_PORT).lookup("queue");

            connectBarrel();
            if (barrelPort == -1) {
                Log.error("[DOWNLOADER " + threadNumber + "] No barrels available on startup. Exiting.");
                return;
            }
            Log.info("[DOWNLOADER " + threadNumber + "] Connected to Barrel on port " + barrelPort);

            while (true) {
                String url = queue.takeUrl();

                if(!url.startsWith("http")) continue;

                Log.url("[DOWNLOADER " + threadNumber + "] Downloading URL: " + url);
                Document doc;
                try {
                    doc = Jsoup.connect(url).get();
                    //System.out.println(doc);
                } catch (HttpStatusException e) {
                    continue;
                }

                ArrayList<String> pageWords = new ArrayList<>();
                ArrayList<String> relatedUrls = new ArrayList<>();

                String text = doc.body().text();
                StringTokenizer st = new StringTokenizer(text, " \t\n\r\f,.:;?![]'\"");

                while (st.hasMoreTokens()) {
                    String token = st.nextToken();
                    // Word max lenght is 64 (it is validated in the regex)
                    if(VALID_WORDS.matcher(token).matches()) pageWords.add(token.toLowerCase());
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
                if(title.length() > 128) title =  title.substring(0, 128).trim();
                String description = "";

                // Try different descriptions/citations from the pages
                String metaDesc = doc.select("meta[name=description]").attr("content");
                if(!metaDesc.isBlank()) description = truncateDescription(metaDesc);

                if(description.isEmpty()){
                    Elements paragraphs = doc.select("p");
                    for (Element p : paragraphs){
                        String paraText = p.text().trim();
                        // Ignore very short descriptions
                        if (text.length() > 15) description = truncateDescription(paraText);
                    }
                }

                if(description.isEmpty()){
                    String bodyText = doc.body().text();
                    description = truncateDescription(bodyText);
                }

                try{
                    if(!barrel.addEntry(url, pageWords, title, description, relatedUrls)){
                        Log.warning("[DOWNLOADER " + threadNumber + "] Failed to parse and/or store an url");
                    }
                } catch (RemoteException e) {
                    Log.error("[DOWNLOADER " + threadNumber + "] Lost connection to Barrel.: " + e.getMessage());
                    barrelPort = -1;
                    if (!attemptReconnect()) {
                        Log.error("[DOWNLOADER " + threadNumber + "] Could not reconnect to any Barrel. Exiting.");
                        return;
                    }
                }
            }
        } catch (IOException | NotBoundException e) {
            Log.error("[DOWNLOADER " + threadNumber + "] Lost connection to Barrel: " + e.getMessage());
        }
    }

    /**
     * Main for Downloader. Starts multiple Downloader threads.
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        int threadCounter = Config.DOWNLOADER_THREADS;

        for (int i = 0; i < threadCounter; i++) {
            new Downloader(i + 1).start();
        }
    }
}
