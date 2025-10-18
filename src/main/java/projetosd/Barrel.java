package projetosd;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Implementation of the Index barrel remote interface.
 * Handles indexing and searching of words across URLs.
 * 
 * @author Jose Amado e José Capinha
 * @version 1.0
 */
public class Barrel extends UnicastRemoteObject implements BarrelInterface {

    /**
     * Counter for the number of URLs parsed.
     */
    private long counter = 0;

    /**
     * Map storing Page metadata for indexed pages.
     */
    private ConcurrentMap<String, Page> pages = new ConcurrentHashMap<>(); //TODO TEM DE SER PASSADO PARA BASE DE DADOS

    /**
     * Map storing indexed words and their associated URLs.
     */
    private ConcurrentMap<String, Set<String>> indexedItems;

    /**
     * Constructs the Barrel.
     * @throws RemoteException
     */
    public Barrel() throws RemoteException {
        super();
        indexedItems = new ConcurrentHashMap<>();
        pages = new ConcurrentHashMap<>();
    }

    /**
     * Adds a word and its associated URL to the index.
     * @param word The word to add
     * @param url The URL where the word was found
     * @throws java.rmi.RemoteException
     */
    @Override
    public synchronized void addToIndex(String word, String url) throws java.rmi.RemoteException {
        // If the word (key) is not there, it creates a new list (synchronized also) and adds the url
        indexedItems.computeIfAbsent(word, k -> Collections.synchronizedSet(new HashSet<>())).add(url);
    }

    /**
     * Search for pages containing all provided terms.
     * @param terms The search terms
     * @returns Returns a list of pages (urls and metadata).
     */
    @Override
    public List<Page> searchQuery(String[] terms) throws java.rmi.RemoteException {
        return new ArrayList<>();
    }

    /**
     * Pings the barrel to check if working.
     * @throws java.rmi.RemoteException
     */
    @Override
    public void ping() throws java.rmi.RemoteException {
    }

    /**
     * Main for Barrel. Starts the RMI registry and binds the barrel.
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        try {
            Barrel barrel = new Barrel();
            Registry registry = LocateRegistry.createRegistry(8183);
            registry.rebind("index", barrel);
            Debug.info("Barrel ready on port: 8183");
        } catch (RemoteException e) {
            Debug.error("Failed to start Barrel: " + e.getMessage());
        }
    }
}
