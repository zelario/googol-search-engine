package projetosd;

import java.rmi.*;
import java.rmi.server.*;
import java.rmi.registry.*;
import java.util.concurrent.*;
import java.util.*;

public class IndexServer extends UnicastRemoteObject implements Index {
    // These structures handle concurrency automatically with good performance (according to gpt friend)
    private final Queue<String> urlsToIndex;
    private final ConcurrentMap<String, List<String>> indexedItems;

    private long counter = 0;

    public IndexServer() throws RemoteException {
        super();

        urlsToIndex = new ConcurrentLinkedDeque<>();
        indexedItems = new ConcurrentHashMap<>();
    }

    public static void main(String[] args) {
        try {
            Scanner sc = new Scanner(System.in);

            IndexServer server = new IndexServer();
            Registry registry = LocateRegistry.createRegistry(8183);
            registry.rebind("index", server);
            System.out.println("Server ready. Waiting for input...\n");
            
            sc.close();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    public synchronized String takeNext() throws RemoteException {
        counter++;
        return urlsToIndex.poll();
    }

    public synchronized void putNew(String url) throws java.rmi.RemoteException {
        urlsToIndex.offer(url);
    }

    public synchronized void addToIndex(String word, String url) throws java.rmi.RemoteException {
        // If the word (key) is not there, it creates a new list (synchronized also) and adds the ulr
        indexedItems.computeIfAbsent(word, k -> Collections.synchronizedList(new ArrayList<>())).add(url);
    }

    public List<String> searchWord(String word) throws java.rmi.RemoteException {
        return indexedItems.get(word);
    }

    public String printStats() throws java.rmi.RemoteException {
        return "URLs parsed: " + counter +
                "\nTotal memory: " + Runtime.getRuntime().totalMemory() / (1024 * 1024) + " MB" +
                "\nFree memory: " + Runtime.getRuntime().freeMemory() / (1024 * 1024) + " MB" +
                "\nMax memory: " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB";
    }
}
