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
            Debug.info("[CLIENT] Connected to gateway on port " + Ports.GATEWAY_PORT);

            System.out.print("---- Welcome to Googol! ----\n\n");
            System.out.print("INDEX: To add new url\nSTATS: To see statistics\nEXIT: To exit the app\n\n");

            boolean run = true;
            try (Scanner scanner = new Scanner(System.in)) {

                System.out.print("Firstly, enter your client ID for logging purposes: ");
                String clientId = scanner.nextLine().trim();
                Debug.info("[CLIENT] Client " + clientId + " has connected.");

                System.out.print("\n>  ");

                while (run) {
                    String query = scanner.nextLine().trim();
                    switch (query) {
                        case "INDEX" -> {
                            System.out.print("Enter URL to add: ");
                            String url = scanner.nextLine().trim();
                            gateway.index(url);
                            System.out.print("URL added successfully.\n");
                        }
                        case "STATS" -> {
                            String stats = gateway.stats(clientId);
                            System.out.println(stats);
                        }
                        case "EXIT" -> {
                            run = false;
                            System.out.println("Exiting the application. Goodbye!");
                            Debug.info("[CLIENT] Client " + clientId + " has disconnected.");
                        }
                        default -> gateway.search(clientId, query);
                    }
                    System.out.print("> ");
                }
            }
        } catch (NotBoundException | RemoteException e) {
            Debug.error("[CLIENT] Gateway not available: " + e.getMessage());
        }
    }
}
