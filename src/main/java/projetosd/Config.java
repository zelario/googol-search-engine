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
        CLIENT_BACKOFF = 500;
        CLIENT_RETRIES = 5;
        GATEWAY_BACKOFF = 200;
        GATEWAY_RETRIES = 3;
        DEBUG = true;
    }

public static synchronized void loadConfiguration() {
    Properties properties = new Properties();
    Path path = Paths.get("config/.properties");

    int defaultGatewayPort = 1098;
    int defaultUrlQueuePort = 1099;
    String defaultBarrelPorts = "1,2,3,4,5";
    int defaultClientRetries = 5;
    int defaultClientBackoff = 500;
    int defaultGatewayRetries = 3;
    int defaultGatewayBackoff = 200;
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

        CLIENT_RETRIES = Integer.parseInt(properties.getProperty("client.retries", String.valueOf(defaultClientRetries)));
        CLIENT_BACKOFF = Integer.parseInt(properties.getProperty("client.backoff", String.valueOf(defaultClientBackoff)));

        GATEWAY_RETRIES = Integer.parseInt(properties.getProperty("gateway.retries", String.valueOf(defaultGatewayRetries)));
        GATEWAY_BACKOFF = Integer.parseInt(properties.getProperty("gateway.backoff", String.valueOf(defaultGatewayBackoff)));

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