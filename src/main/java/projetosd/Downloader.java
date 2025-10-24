package projetosd;

import java.io.IOException;
import java.rmi.NotBoundException;
import java.rmi.registry.LocateRegistry;
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
 * @author Jose Amado & José Capinha
 * @version 1.0
 */
public class Downloader extends Thread { //TODO Downloaders tem de ser capazes de encontrar um barrel novo se o que estiver ligado falhar

    /**
     * The thread number for this downloader instance.
     */
    private final int threadNumber;

    /**
     * Constructs a Downloader.
     * @param threadNum The thread number
     */
    public Downloader(int threadNum) {
        this.threadNumber = threadNum;
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
            Debug.info("[DOWNLOADER " + threadNumber + "] Starting downloader thread.");
            UrlQueueInterface queue = (UrlQueueInterface) LocateRegistry.getRegistry(Ports.URL_QUEUE_PORT).lookup("queue");

            int connectedBarrelPort = Ports.lookBarrels();
            BarrelInterface barrel = (BarrelInterface) LocateRegistry.getRegistry(connectedBarrelPort).lookup("barrel");
            Debug.info("[DOWNLOADER " + threadNumber + "] Connected to Barrel on port " + connectedBarrelPort);

            while (true) {
                String url = queue.takeUrl();

                if(!url.startsWith("http")) continue;

                Debug.url("[DOWNLOADER " + threadNumber + "] Downloading URL: " + url);
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
                    // Word max lenght is 64
                    if(token.length() <= 64 ) pageWords.add(token.toLowerCase());
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

                // TODO: add failback logic
                if(!barrel.addEntry(url, pageWords, title, description, relatedUrls)){
                    Debug.info("[DOWNLOADER " + threadNumber + "] Failed to parse and/or store an url");
                }
            }
        } catch (IOException | NotBoundException e) {
            Debug.error("[DOWNLOADER " + threadNumber + "] " + e.getMessage());
        }
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
}
