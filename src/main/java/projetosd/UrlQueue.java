package projetosd;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.LinkedBlockingQueue;

public class UrlQueue extends UnicastRemoteObject implements UrlQueueInterface{
    // Handles (thread) concurrency automatically, no need for extra logic
    private final LinkedBlockingQueue<String> urlQueue = new LinkedBlockingQueue<>();

    public UrlQueue() throws java.rmi.RemoteException {
        super();
    }

    public static void main(String[] args) {
        try{
            UrlQueue queue =  new UrlQueue();

            Registry registry = LocateRegistry.createRegistry(1099);
            registry.rebind("queue", queue);
            System.out.println("Queue ready");
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    @Override
    public void addUrl(String url) throws RemoteException {
        urlQueue.add(url);
        System.out.println("[urlQueue] Added url: " + url);
    }

    @Override
    public String takeUrl() throws RemoteException {
        return urlQueue.poll();
    }

    @Override
    public boolean isEmpty() throws RemoteException {
        return urlQueue.isEmpty();
    }
}
