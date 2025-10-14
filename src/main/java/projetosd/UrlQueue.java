package projetosd;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.LinkedBlockingDeque;

public class UrlQueue extends UnicastRemoteObject implements UrlQueueInterface{
    // Handles (thread) concurrency automatically, no need for extra logic
    private final LinkedBlockingDeque<String> urlQueue = new LinkedBlockingDeque<>();

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
    public void addUrl(String url, boolean userInput) throws RemoteException {
        // If userInput -> insert first so user input is processed first, if not, insert normally (FIFO)
        if(userInput) urlQueue.addFirst(url);
        else urlQueue.add(url);

        System.out.println("[urlQueue] Added url: " + url);
    }

    @Override
    public String takeUrl() throws RemoteException {
        try{
            return urlQueue.take();
        }
        catch (InterruptedException e){
            return null;
        }
    }

}
