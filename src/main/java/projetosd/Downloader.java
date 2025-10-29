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

    private GatewayInterface gateway;

    /**
     * Constructs a Downloader.
     * @param threadNum The thread number
     */
    public Downloader(int threadNum) {
        this.threadNumber = threadNum;
    }

    /**
     * Try to find any available barrel from configured ports and set the `barrel` and `barrelPort` fields.
     * @returns False if it works, true if not. This is to activate any action when it does not work.
     */
    private boolean connectGateway() {
        try {
            this.gateway = (GatewayInterface) LocateRegistry.getRegistry(Config.GATEWAY_PORT).lookup("gateway");
            return false;
        } catch (NotBoundException | RemoteException ignored) {return true;}
    }

    /**
     * Attempt to reconnect to any barrel with retries and backoff.
     * @return true if reconnected, false otherwise
     */
    @SuppressWarnings({"SleepWhileInLoop", "BusyWait"})
    private boolean attemptReconnect() {
        for (int attempt = 1; attempt <= Config.DOWNLOADER_RETRIES; attempt++) {
            Log.info("[DOWNLOADER " + threadNumber + "] Attempt " + attempt + " to reconnect to a barrel.");
            if (connectGateway()) {
                Log.info("[DOWNLOADER " + threadNumber + "] Reconnected to Gateway on port " + Config.GATEWAY_PORT + " (attempt " + attempt + ")");
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
            Log.info("[DOWNLOADER " + threadNumber + "] Connected to url queue on port " + Config.URL_QUEUE_PORT);

            if(connectGateway()) attemptReconnect();

            Log.info("[DOWNLOADER " + threadNumber + "] Connected to gateway on port " + Config.GATEWAY_PORT);

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
                    // If no barrel got the info, re-insert url in url queue
                    if(!gateway.multicastEntries(url, pageWords, title, description, relatedUrls)){
                        queue.addUrl(url, false);
                    }

                } catch (RemoteException e) {
                    Log.error("[DOWNLOADER " + threadNumber + "] Lost connection to Gateway: " + e.getMessage());
                    if (!attemptReconnect()) {
                        Log.error("[DOWNLOADER " + threadNumber + "] Could not reconnect to Gateway. Exiting.");
                        return;
                    }
                }
            }
        } catch (IOException | NotBoundException e) {
            Log.error("[DOWNLOADER " + threadNumber + "] Lost connection to Gateway: " + e.getMessage());
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
