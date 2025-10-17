package projetosd;

import java.rmi.Remote;
import java.util.Set;

/**
 * Remote interface for the distributed index server.
 * 
 * @author Jose Amado e José Capinha
 * @version 1.0
 */
public interface BarrelInterface extends Remote {
    /**
     * Adds a word and its associated URL to the index.
     * @param word The word to add
     * @param url The URL where the word was found
     * @throws java.rmi.RemoteException if a remote error occurs
     */
    void addToIndex(String word, String url) throws java.rmi.RemoteException;

    /**
     * Searches for a word in the index and returns the set of URLs where it appears.
     * @param word The word to search for
     * @return Set of URLs containing the word
     * @throws java.rmi.RemoteException if a remote error occurs
     */
    Set<String> searchWord(String word) throws java.rmi.RemoteException;

    /**
     * Prints statistics about the index.
     * @return A string with statistics
     * @throws java.rmi.RemoteException if a remote error occurs
     */
    String printStats() throws java.rmi.RemoteException;

    /**
     * Pings the server to check if working.
     * @throws java.rmi.RemoteException if a remote error occurs
     */
    void ping() throws java.rmi.RemoteException;
}
