package projetosd;

import java.rmi.registry.LocateRegistry;
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
            IndexServerInterface index = (IndexServerInterface) LocateRegistry.getRegistry(8183).lookup("index");
            UrlQueueInterface queue = (UrlQueueInterface) LocateRegistry.getRegistry(1099).lookup("queue");

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
                    index.addToIndex(st.nextToken(), url);
                }

                Elements links = doc.select("a[href]");

                for (Element link : links) {
                    String pageUrl = link.attr("href");
                    if ((pageUrl.startsWith("https://"))) {
                        queue.addUrl(pageUrl, false);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
