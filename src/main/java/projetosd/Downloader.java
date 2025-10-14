package projetosd;

import java.rmi.registry.*;
import java.util.*;

import org.jsoup.*;
import org.jsoup.nodes.*;
import org.jsoup.select.*;

public class Downloader extends Thread{
    private final int thread_num;

    public Downloader(int thread_num){
        this.thread_num = thread_num;
    }

    public static void main(String[] args){
        // CHANGE THREAD NUMBER HERE
        int thread_counter = 5;

        for (int i = 0; i < thread_counter; i++){
            new Downloader(i+1).start();
        }
    }

    public void run(){
        try {
            Index index = (Index) LocateRegistry.getRegistry(8183).lookup("index");
            UrlQueueInterface queue = (UrlQueueInterface) LocateRegistry.getRegistry(1099).lookup("queue");
            while (true) {
                String url = queue.takeUrl();

                System.out.println(thread_num + ": " + url);
                Document doc;
                try{
                    doc = Jsoup.connect(url).get();
                    //System.out.println(doc);
                }
                catch(HttpStatusException e){
                    continue;
                }

                String text = doc.body().text();
                StringTokenizer st = new StringTokenizer(text, " \t\n\r\f,.:;?![]'\"");

                while(st.hasMoreTokens()) {
                    index.addToIndex(st.nextToken(), url);
                }

                Elements links = doc.select("a[href]");

                for (Element link : links) {
                    String page_url = link.attr("href");
                    if((page_url.startsWith("https://"))){
                        queue.addUrl(page_url, false);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
