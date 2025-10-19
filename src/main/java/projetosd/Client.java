package projetosd;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
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
            Registry registry = LocateRegistry.getRegistry(Ports.GATEWAY_PORT);
            GatewayInterface gateway = (GatewayInterface) registry.lookup("gateway");
            System.out.println("Connected to gateway on port " + Ports.GATEWAY_PORT);

        } catch (NotBoundException | RemoteException e) {
            Debug.error("Gateway not available: " + e.getMessage());
        }
    }
}
