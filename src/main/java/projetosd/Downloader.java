package projetosd;

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
                if(!Barrel.addEntry(url, pageWords, title, description, relatedUrls)){
                    System.out.println("[DOWNLOADER] Failed to parse and store an url");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     *  Truncates description to reduce citation size
     *
     * @param description   Text to be truncated or not
     * @return              (If necessary) Trimmed text
     */
    private static String truncateDescription(String description){
        int maxLength = 30;

        if (description.length() < maxLength) return description;

        int periodIndex = description.indexOf(".", maxLength);
        if(periodIndex != -1) return description.substring(0, periodIndex + 1).trim();

        return description.substring(0, maxLength).trim() + "...";
    }
}
