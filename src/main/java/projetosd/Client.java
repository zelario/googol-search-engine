package projetosd;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

/**
 * Console for the Gateway RMI server.
 * 
 * @author Jose Amado e José Capinha
 * @version 1.0
 */
public class Client {
    public static void main(String[] args) {
        try {
            int GATEWAY_PORT = 1098;
            Registry registry = LocateRegistry.getRegistry(GATEWAY_PORT);
            GatewayInterface gateway = (GatewayInterface) registry.lookup("gateway");
            System.out.println("Connected to gateway on port " + GATEWAY_PORT);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
