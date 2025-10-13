package projetosd;

import java.rmi.Remote;

public interface UrlQueueInterface extends Remote {
    String takeUrl() throws java.rmi.RemoteException;
    void addUrl(String url) throws java.rmi.RemoteException;
    boolean isEmpty() throws java.rmi.RemoteException;
}
