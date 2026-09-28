package projetosd;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Properties;

/**
 * Ports management class.
 * Contains constants for RMI ports and methods to claim barrel ports.
 */
public class Config {

    /**
     * Gateway RMI Host
     */
    public static String GATEWAY_HOST;

    /**
     * WebSocket host for stats
     */
    public static String WEBSOCKET_HOST;

    /**
     * WebSocket port for stats
     */
    public static int WEBSOCKET_PORT;

    /**
     * Gateway RMI port
     */
    public static int GATEWAY_PORT;

    /**
     * URL Queue RMI port
     */
    public static int URL_QUEUE_PORT;

    /**
     * URL Queue RMI host
     */
    public static String URL_QUEUE_HOST;

    /**
     * Barrel RMI ports
     */
    public static int[] BARREL_PORTS;

    /**
     * Barrel RMI hosts
     */
    public static String[] BARREL_HOSTS;

    /**
     * Downloader threads number
     */
    public static int DOWNLOADER_THREADS;

    /**
     * Client backoff settings
     */
    public static int CLIENT_BACKOFF;

    /**
     * Client retry settings
     */
    public static int CLIENT_RETRIES;

    /**
     * Gateway backoff settings
     */
    public static int GATEWAY_BACKOFF;

    /**
     * Gateway retry settings
     */
    public static int GATEWAY_RETRIES;

    /**
     * Gateway sync scheduler interval
     */
    public static int GATEWAY_SYNC_INTERVAL;

    /**
     * Barrel backoff settings
     */
    public static int BARREL_BACKOFF;

    /**
     * Barrel retry settings
     */
    public static int BARREL_RETRIES;

    /**
     * Downloader backoff settings
     */
    public static int DOWNLOADER_BACKOFF;

    /**
     * Downloader retry settings
     */
    public static int DOWNLOADER_RETRIES;

    /**
     * Downloader page connection timeout
     */
    public static int DOWNLOADER_CONNECTION_TIMEOUT;

    /**
     * Downloader wait time between url queue connection attempts
     */
    public static int DOWNLOADER_QUEUE_WAIT;

    /**
     * Debug mode
     */
    public static boolean DEBUG;

    /**
     * Table with Port associated Host
     */
    public static HashMap<Integer, String> BARREL_HOSTS_TRANSLATION_TABLE;

    /**
     * Stopwords percentile
     */
    public static float STOPWORDS_PERCENTILE;

    /**
     * Static initializer to load configuration at class load time.
     */
    static {
        loadConfiguration();
    }

    /**
     * Applies defaults if no values are found
     */
    private static void applyDefaults() {
        GATEWAY_HOST = "localhost";
        GATEWAY_PORT = 1098;
        WEBSOCKET_HOST = "localhost";
        WEBSOCKET_PORT = 8080;
        URL_QUEUE_HOST = "localhost";
        URL_QUEUE_PORT = 1099;
        BARREL_HOSTS = new String[]{"localhost", "localhost", "localhost", "localhost", "localhost"};
        BARREL_PORTS = new int[]{1100, 1101, 1102, 1103, 1104};
        DOWNLOADER_THREADS = 3;
        CLIENT_BACKOFF = 500;
        CLIENT_RETRIES = 5;
        GATEWAY_BACKOFF = 200;
        GATEWAY_RETRIES = 3;
        GATEWAY_SYNC_INTERVAL = 5;
        BARREL_BACKOFF = 300;
        BARREL_RETRIES = 3;
        DOWNLOADER_BACKOFF = 300;
        DOWNLOADER_RETRIES = 3;
        DOWNLOADER_CONNECTION_TIMEOUT = 10000;
        DOWNLOADER_QUEUE_WAIT = 5000;
        STOPWORDS_PERCENTILE = 0.9999f;
        DEBUG = true;

        BARREL_HOSTS_TRANSLATION_TABLE = new HashMap<>();

        for(int i = 0; i < BARREL_PORTS.length; i++){
            BARREL_HOSTS_TRANSLATION_TABLE.put(BARREL_PORTS[i], BARREL_HOSTS[i]);
        }
    }

    /**
     * Loads configuration from .properties
     */
    public static synchronized void loadConfiguration() {
        Properties properties = new Properties();
        Path path = Paths.get("config/.properties");

        String defaultGatewayHost = "localhost";
        int defaultGatewayPort = 1098;
        String defaultWebSocketHost = "localhost";
        int defaultWebSocketPort = 8080;
        String defaultUrlQueueHost = "localhost";
        int defaultUrlQueuePort = 1099;
        String defaultBarrelHosts = "localhost, localhost, localhost, localhost, localhost";
        String defaultBarrelPorts = "1100, 1101, 1102, 1103, 1104";
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
        int defaultDownloaderConnectionTimeout = 10000;
        int defaultDownloaderQueueWait = 5000;
        float defaultStopwordsPercentile = 0.9999f;
        boolean defaultDebug = true;

        if (!Files.exists(path)) {
            applyDefaults();
            return;
        }


        try (FileInputStream fis = new FileInputStream(path.toFile())) {
            properties.load(fis);

            GATEWAY_HOST = properties.getProperty("gateway.host", defaultGatewayHost);
            URL_QUEUE_HOST = properties.getProperty("url_queue.host", defaultUrlQueueHost);

            GATEWAY_PORT = Integer.parseInt(properties.getProperty("gateway.port", String.valueOf(defaultGatewayPort)));
            URL_QUEUE_PORT = Integer.parseInt(properties.getProperty("url_queue.port", String.valueOf(defaultUrlQueuePort)));

            // WebSocket config
            WEBSOCKET_HOST = properties.getProperty("websocket.host", defaultWebSocketHost);
            WEBSOCKET_PORT = Integer.parseInt(properties.getProperty("websocket.port", String.valueOf(defaultWebSocketPort)));

            String barrelHostsRaw = properties.getProperty("barrel.hosts", defaultBarrelHosts);
            BARREL_HOSTS = barrelHostsRaw.split(",");

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
            GATEWAY_SYNC_INTERVAL = Integer.parseInt(properties.getProperty("gateway.sync.interval", String.valueOf(defaultGatewaySyncInterval)));

            BARREL_RETRIES = Integer.parseInt(properties.getProperty("barrel.retries", String.valueOf(defaultBarrelRetries)));
            BARREL_BACKOFF = Integer.parseInt(properties.getProperty("barrel.backoff", String.valueOf(defaultBarrelBackoff)));

            DOWNLOADER_RETRIES = Integer.parseInt(properties.getProperty("downloader.retries", String.valueOf(defaultDownloaderRetries)));
            DOWNLOADER_BACKOFF = Integer.parseInt(properties.getProperty("downloader.backoff", String.valueOf(defaultDownloaderBackoff)));
            DOWNLOADER_CONNECTION_TIMEOUT = Integer.parseInt(properties.getProperty("downloader.connection.timeout", String.valueOf(defaultDownloaderConnectionTimeout)));
            DOWNLOADER_QUEUE_WAIT = Integer.parseInt(properties.getProperty("downloader.queue.wait", String.valueOf(defaultDownloaderQueueWait)));

            STOPWORDS_PERCENTILE = Float.parseFloat(properties.getProperty("stopwords.percentile", String.valueOf(defaultStopwordsPercentile)));

            DEBUG = Boolean.parseBoolean(properties.getProperty("debug.enabled", String.valueOf(defaultDebug)));

            BARREL_HOSTS_TRANSLATION_TABLE = new HashMap<>();
            for(int i = 0; i < BARREL_PORTS.length; i++){
                if(i < BARREL_HOSTS.length){
                    BARREL_HOSTS_TRANSLATION_TABLE.put(BARREL_PORTS[i], BARREL_HOSTS[i]);
                }
                else BARREL_HOSTS_TRANSLATION_TABLE.put(BARREL_PORTS[i], "localhost");
            }

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
           try {
                new ServerSocket(port, 0, InetAddress.getByName(BARREL_HOSTS_TRANSLATION_TABLE.get(port))).close();
                return port;
            } catch (IOException e) {
                Log.info("[PORTS] Port already in use: " + port);
            }
        }
        return -1; 
    }
}