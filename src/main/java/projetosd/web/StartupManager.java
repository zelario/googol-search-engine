package projetosd.web;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import projetosd.Barrel;
import projetosd.BarrelInterface;
import projetosd.Config;
import projetosd.Downloader;
import projetosd.Gateway;
import projetosd.GatewayInterface;
import projetosd.Log;
import projetosd.UrlQueue;

/**
 * Startup Manager to initialize components on application start.
 * 
 * Manages the initialization of gateway, url queue, barrels, and downloaders when the application starts.
 */
@Component
public class StartupManager implements ApplicationRunner {

    /**
     * Gateway instance
     */
    @Value("${gateway.instance}")
    private int gatewayInstance;

    /**
     * UrlQueue instance
     */
    @Value("${urlqueue.instance}")
    private int urlQueueInstance;

    /**
     * Number of barrels to initialize.
     */
    @Value("${barrel.number}")
    private int barrelNumber;

    /**
     * Number of downloaders to initialize.
     */
    @Value("${downloader.number}")
    private int downloaderNumber;

    /**
     * Gateway Manager to initialize the gateway.
     */
    @Component
    public class GatewayManager {

        /**
         * Initializes the gateway component.
         */
        public void init() {

            if(gatewayInstance==0) return;
            
            Log.clearLog();
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

            try {

                Gateway gateway = new Gateway();

                System.setProperty("java.rmi.server.hostname", Config.GATEWAY_HOST);
                Registry registry = LocateRegistry.createRegistry(Config.GATEWAY_PORT);
                registry.rebind("gateway", gateway);

                Log.info("[GATEWAY] Gateway ready on " + Config.GATEWAY_HOST + ":" + Config.GATEWAY_PORT);
                gateway.reviveGateway();

                scheduler.scheduleAtFixedRate(() -> {
                    try{

                        gateway.syncBarrels(0);

                        for(BarrelInterface barrel : gateway.barrels.values()){
                            barrel.checkStopWords();
                        }

                    } catch (RemoteException e) {
                        Log.warning("[GATEWAY] Periodic synchronizer will not be scheduled");
                    }
                }, 0, Config.GATEWAY_SYNC_INTERVAL, TimeUnit.MINUTES);

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    Log.info("[GATEWAY] Gateway shutting down");
                }));

            } catch (RemoteException e) {
                Log.error("[GATEWAY] Failed to start Gateway RMI server: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    /**
     * URL Queue Manager to initialize the URL queue.
     */
    @Component
    public class URLQueueManager {

        /**
         * Initializes the URL queue component.
         */
        public void init() {

            if(urlQueueInstance==0) return;

            try {
                UrlQueue queue = new UrlQueue();

                System.setProperty("java.rmi.server.hostname", Config.URL_QUEUE_HOST);
                Registry registry = LocateRegistry.createRegistry(Config.URL_QUEUE_PORT);
                registry.rebind("queue", queue);
                Log.info("[URLQueue] RMI server ready");

                queue.loadQueue();

                registry = LocateRegistry.getRegistry(Config.GATEWAY_HOST, Config.GATEWAY_PORT);
                queue.gateway = (GatewayInterface) registry.lookup("gateway");
                queue.gateway.reportQueueStatus(true);
                Log.info("[URLQueue] Connected to Gateway on port " + Config.GATEWAY_PORT);

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    //queue.saveQueue();
                    
                    try {
                        queue.gateway.reportQueueStatus(false);
                    } catch (RemoteException e) {
                        Log.error("[URLQueue] Could not notify Gateway of shutdown: " + e.getMessage());
                    }

                    Log.info("[URLQueue] Exiting");
                }));
                
            } catch (RemoteException | NotBoundException e) {
                Log.error("[URLQueue] Exiting. Could not start RMI server: " + e.getMessage());
                System.exit(1);
            }
        }
    }

    /**
     * Downloader Manager to initialize downloaders.
     */
    @Component  
    public class DownloaderManager {

        /**
         * Initializes the downloader components.
         */
        public void init() {

            int threadCounter = Config.DOWNLOADER_THREADS;

            for (int process = 0; process < downloaderNumber; process++) {
                for (int thread = process*threadCounter; thread < (process+1)*threadCounter; thread++) {
                    new Downloader(thread + 1).start();
                }
            }
        }
    }

    /**
     * Barrel Manager to initialize barrels.
     */
    @Component
    public class BarrelManager {

        /**
         * Initializes the barrel components.
         */
        public void init() {

            for (int i = 0; i < barrelNumber; i++) {

                try {
                    Barrel barrel = new Barrel();

                    System.setProperty("java.rmi.server.hostname",  Config.BARREL_HOSTS_TRANSLATION_TABLE.get(barrel.port));
                    Registry registry = LocateRegistry.createRegistry(barrel.port);
                    registry.rebind("barrel", barrel);
                    Log.info("[BARREL " + barrel.port + "] Running on " + Config.BARREL_HOSTS_TRANSLATION_TABLE.get(barrel.port) + ":" + barrel.port);

                    registry = LocateRegistry.getRegistry(Config.GATEWAY_HOST, Config.GATEWAY_PORT);
                    barrel.gateway = (GatewayInterface) registry.lookup("gateway");

                    Log.info("[BARREL " + barrel.port + "] Requesting sync to gateway");
                    if(barrel.gateway.syncBarrels(barrel.port)){
                        Log.info("[BARREL " + barrel.port + "] Sync successful");
                    }

                    barrel.gateway.reportBarrelStatus(barrel.port, true);
                    Log.info("[BARREL " + barrel.port + "] Registered with gateway on " + Config.GATEWAY_HOST + ":" + Config.GATEWAY_PORT);

                    barrel.reportIndexSize();

                    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                        try {
                            if (barrel.gateway == null) {
                                Registry reg = LocateRegistry.getRegistry(Config.GATEWAY_HOST, Config.GATEWAY_PORT);
                                barrel.gateway = (GatewayInterface) reg.lookup("gateway");
                            }
                            barrel.gateway.reportBarrelStatus(barrel.port, false);
                            Log.info("[BARREL " + barrel.port + "] Shutdown notification sent to gateway. Exiting.");
                        } catch (NotBoundException | RemoteException e) {
                            Log.error("[BARREL " + barrel.port + "] Failed to notify gateway on shutdown: " + e.getMessage());
                        }
                    }));

                } catch (RemoteException | NotBoundException e) {
                    Log.error("[BARREL] Failed to start Barrel RMI Server: " + e.getMessage());
                    System.exit(1);
                }
            }
        }
    }

    /**
     * Run method to initialize components on application start.
     * @param args The application arguments.
     */
    @Override
    public void run(ApplicationArguments args) {
        new GatewayManager().init();
        new URLQueueManager().init();
        new BarrelManager().init();
        new DownloaderManager().init();
    }
}