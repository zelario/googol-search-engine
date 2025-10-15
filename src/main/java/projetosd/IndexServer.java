package projetosd;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collections;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of the Index Server remote interface.
 * Handles indexing and searching of words across URLs.
 * 
 * @author Jose Amado e José Capinha
 * @version 1.0
 */
public class IndexServer extends UnicastRemoteObject implements IndexServerInterface {

    /**
     * Map storing indexed words and their associated URLs.
     */
    private final ConcurrentMap<String, Set<String>> indexedItems;

    /**
     * Counter for the number of URLs parsed.
     */
    private long counter = 0;

    /**
     * Constructs the IndexServer.
     * @throws RemoteException if a remote error occurs
     */
    public IndexServer() throws RemoteException {
        super();
        indexedItems = new ConcurrentHashMap<>();
    }

    /**
     * Main for IndexServer. Starts the RMI registry and binds the server.
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        try {
            Scanner scanner = new Scanner(System.in);

            IndexServer server = new IndexServer();
            Registry registry = LocateRegistry.createRegistry(8183);
            registry.rebind("index", server);
            System.out.println("Server ready. Waiting for input...\n");

            scanner.close();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    /**
     * Adds a word and its associated URL to the index.
     * @param word The word to add
     * @param url The URL where the word was found
     * @throws java.rmi.RemoteException if a remote error occurs
     */
    @Override
    public synchronized void addToIndex(String word, String url) throws java.rmi.RemoteException {
        // If the word (key) is not there, it creates a new list (synchronized also) and adds the url
        indexedItems.computeIfAbsent(word, k -> Collections.synchronizedSet(new HashSet<>())).add(url);
    }

    /**
     * Searches for a word in the index and returns the set of URLs where it appears.
     * @param word The word to search for
     * @return Set of URLs containing the word
     * @throws java.rmi.RemoteException if a remote error occurs
     */
    @Override
    public Set<String> searchWord(String word) throws java.rmi.RemoteException {
        return indexedItems.get(word);
    }

    /**
     * Prints statistics about the index.
     * @return A string with statistics
     * @throws java.rmi.RemoteException if a remote error occurs
     */
    @Override
    public String printStats() throws java.rmi.RemoteException {
        return "URLs parsed: " + counter +
                "\nTotal memory: " + Runtime.getRuntime().totalMemory() / (1024 * 1024) + " MB" +
                "\nFree memory: " + Runtime.getRuntime().freeMemory() / (1024 * 1024) + " MB" +
                "\nMax memory: " + Runtime.getRuntime().maxMemory() / (1024 * 1024) + " MB";
    }

    /**
     * Pings the server to check if working.
     * @throws java.rmi.RemoteException if a remote error occurs
     */
    @Override
    public void ping() throws java.rmi.RemoteException {
    }

}
