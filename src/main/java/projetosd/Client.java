package projetosd;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;

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
            Debug.info("Connected to gateway on port " + Ports.GATEWAY_PORT);

            System.out.println("---- Welcome to Googol! ----\n\n");
            System.out.println("STATS: To see statistics\n EXIT: To exit the app\n");

            boolean run=true;
            while(run){
                try (Scanner scanner = new Scanner(System.in)) {
                    String query = scanner.nextLine().trim();
                    switch (query) {
                        case "STATS" -> {
                            String stats = gateway.stats();
                            System.out.println(stats);
                        }
                        case "EXIT" -> {
                            run = false;
                            System.out.println("Exiting the application. Goodbye!");
                        }
                        default -> gateway.search(query);
                    }
                }
            }
        } catch (NotBoundException | RemoteException e) {
            Debug.error("Gateway not available: " + e.getMessage());
        }
    }
}
