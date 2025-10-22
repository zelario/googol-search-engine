package projetosd;

import java.io.IOException;
import java.net.ServerSocket;

public class Ports {

    /*
     * Gateway RMI port
     */
    public static final int GATEWAY_PORT = 1098;

    /*
     * URL Queue RMI port
     */
    public static final int URL_QUEUE_PORT = 1099;

    /*
     * Barrel RMI ports
     */
    public static final int BARREL_PORTS[] = {2000, 2001, 2002, 2003};
    
    /**
     * Gets the next available barrel port.
     * @return The next available port, or -1 if no ports are available
     */
    public static int claimBarrelPort() {
        for (int port : BARREL_PORTS) {
            try (ServerSocket socket = new ServerSocket(port)) {
                return port;
            } catch (IOException e) {}
        }
        return -1; 
    }
}