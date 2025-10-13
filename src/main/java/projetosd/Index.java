package projetosd;

import java.rmi.*;
import java.util.*;

public interface Index extends Remote {
    void addToIndex(String word, String url) throws java.rmi.RemoteException;
    List<String> searchWord(String word) throws java.rmi.RemoteException;
    String printStats() throws java.rmi.RemoteException;
}
