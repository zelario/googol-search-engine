package projetosd;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Ports management class.
 * Contains constants for RMI ports and methods to claim barrel ports.
 * 
 * @author Jose Amado & José Capinha
 * @version 1.0
 */
public class Config {

    /*
     * Gateway RMI port
     */
    public static int GATEWAY_PORT;

    /*
     * URL Queue RMI port
     */
    public static int URL_QUEUE_PORT;

    /*
     * Barrel RMI ports
     */
    public static int[] BARREL_PORTS;

    /*
     * Downloader threads number
     */
    public static int DOWNLOADER_THREADS;

    /*
     * Client backoff settings
     */
    public static int CLIENT_BACKOFF;

    /*
     * Client retry settings
     */
    public static int CLIENT_RETRIES;

    /*
     * Gateway backoff settings
     */
    public static int GATEWAY_BACKOFF;

    /*
     * Gateway retry settings
     */
    public static int GATEWAY_RETRIES;

    /*
     * Gateway sync scheduler interval
     */
    public static int GATEWAY_SYNCH_INTERVAL;

    /*
     * Barrel backoff settings
     */
    public static int BARREL_BACKOFF;

    /*
     * Barrel retry settings
     */
    public static int BARREL_RETRIES;

    /*
     * Downloader backoff settings
     */
    public static int DOWNLOADER_BACKOFF;

    /*
     * Downloader retry settings
     */
    public static int DOWNLOADER_RETRIES;

    /*
     * Debug mode
     */
    public static boolean DEBUG;

    static {
        loadConfiguration();
    }

    private static void applyDefaults() {
        GATEWAY_PORT = 1098;
        URL_QUEUE_PORT = 1099;
        BARREL_PORTS = new int[]{1,2,3,4,5};
        DOWNLOADER_THREADS = 3;
        CLIENT_BACKOFF = 500;
        CLIENT_RETRIES = 5;
        GATEWAY_BACKOFF = 200;
        GATEWAY_RETRIES = 3;
        GATEWAY_SYNCH_INTERVAL = 5;
        BARREL_BACKOFF = 300;
        BARREL_RETRIES = 3;
        DOWNLOADER_BACKOFF = 300;
        DOWNLOADER_RETRIES = 3;
        DEBUG = true;
    }

public static synchronized void loadConfiguration() {
    Properties properties = new Properties();
    Path path = Paths.get("config/.properties");

    int defaultGatewayPort = 1098;
    int defaultUrlQueuePort = 1099;
    String defaultBarrelPorts = "1,2,3,4,5";
    int defaultDownloaderThreads = 3;
    int defaultClientRetries = 5;
    int defaultClientBackoff = 500;
    int defaultGatewayRetries = 3;
    int defaultGatewayBackoff = 200;
    int defaultGatewaySyncInterval = 5;
    int defaultBarrelRetries = 3;
    int defaultBarrelBackoff = 300;
    int defaultDownloaderRetries = 3;
    int defaultDownloaderBackoff = 300;
    boolean defaultDebug = true;

    if (!Files.exists(path)) {
        applyDefaults();
        return;
    }

    try (FileInputStream fis = new FileInputStream(path.toFile())) {
        properties.load(fis);

        GATEWAY_PORT = Integer.parseInt(properties.getProperty("gateway.port", String.valueOf(defaultGatewayPort)));
        URL_QUEUE_PORT = Integer.parseInt(properties.getProperty("url_queue.port", String.valueOf(defaultUrlQueuePort)));

        String barrelPortsStr = properties.getProperty("barrel.ports", defaultBarrelPorts);
        String[] portsArr = barrelPortsStr.split(",");
        BARREL_PORTS = new int[portsArr.length];
        for (int i = 0; i < portsArr.length; i++) {
            BARREL_PORTS[i] = Integer.parseInt(portsArr[i].trim());
        }

        DOWNLOADER_THREADS = Integer.parseInt(properties.getProperty("downloader.threads", String.valueOf(defaultDownloaderThreads)));

        CLIENT_RETRIES = Integer.parseInt(properties.getProperty("client.retries", String.valueOf(defaultClientRetries)));
        CLIENT_BACKOFF = Integer.parseInt(properties.getProperty("client.backoff", String.valueOf(defaultClientBackoff)));

        GATEWAY_RETRIES = Integer.parseInt(properties.getProperty("gateway.retries", String.valueOf(defaultGatewayRetries)));
        GATEWAY_BACKOFF = Integer.parseInt(properties.getProperty("gateway.backoff", String.valueOf(defaultGatewayBackoff)));
        GATEWAY_SYNCH_INTERVAL = Integer.parseInt(properties.getProperty("gateway.sync.interval", String.valueOf(defaultGatewaySyncInterval)));

        BARREL_RETRIES = Integer.parseInt(properties.getProperty("barrel.retries", String.valueOf(defaultBarrelRetries)));
        BARREL_BACKOFF = Integer.parseInt(properties.getProperty("barrel.backoff", String.valueOf(defaultBarrelBackoff)));

        DOWNLOADER_RETRIES = Integer.parseInt(properties.getProperty("downloader.retries", String.valueOf(defaultDownloaderRetries)));
        DOWNLOADER_BACKOFF = Integer.parseInt(properties.getProperty("downloader.backoff", String.valueOf(defaultDownloaderBackoff)));

        DEBUG = Boolean.parseBoolean(properties.getProperty("debug.enabled", String.valueOf(defaultDebug)));

    } catch (IOException | NumberFormatException e) {
        applyDefaults();
    }
}

    /**
     * Gets the next available barrel port.
     * @return The next available port, or -1 if no ports are available
     */
    public static int claimBarrelPort() {
        for (int port : BARREL_PORTS) {
            try (ServerSocket ignored = new ServerSocket(port)) {
                return port;
            } catch (IOException e) {
                Log.info("[PORTS] Port already in use: " + port);
            }
        }
        return -1; 
    }
}