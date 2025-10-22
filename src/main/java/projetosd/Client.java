package projetosd;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Scanner;

/**
 * Console for the Gateway RMI server.
 * 
 * @author Jose Amado & José Capinha
 * @version 1.0
 */
public class Client {
    /**
     * Main to run the client console.
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        try {
            Registry registry = LocateRegistry.getRegistry(Ports.GATEWAY_PORT);
            GatewayInterface gateway = (GatewayInterface) registry.lookup("gateway");
            Debug.info("[CLIENT] Connected to gateway on port " + Ports.GATEWAY_PORT);

            System.out.print("===== Welcome to Googol! =====\n\n");
            System.out.print("SEARCH: To search for a url\nINDEX: To add new url\nSTATS: To see statistics\nEXIT: To exit the app\n\n");

            boolean run = true;
            String mode = "SEARCH";

            try (Scanner scanner = new Scanner(System.in)) {

                System.out.print("-Firstly, enter your client ID for logging purposes: ");
                String clientId = scanner.nextLine().trim();
                Debug.info("[CLIENT] Client " + clientId + " has connected.");

                System.out.print("\n>  ");

                while (run) {
                    String query = scanner.nextLine().trim();
                    
                    if (!mode.equals("SEARCH") && query.equals("SEARCH")) {
                        mode = "SEARCH";
                        System.out.print("\n=== SEARCH MODE ===\n\n> ");
                        continue;
                    } else if (!mode.equals("INDEX") && query.equals("INDEX")) {
                        mode = "INDEX";
                        System.out.print("\n=== INDEX MODE ===\n\n> ");
                        continue;
                    } else if (query.equals("STATS")) {
                        gateway.stats(clientId);
                        continue;
                    } else if (query.equals("EXIT")) {
                        run = false;
                        System.out.println("=== Exiting. Goodbye! ===");
                    }

                    switch (mode) {
                        case "SEARCH" -> {
                            List<List<Page>> results = gateway.search(query, clientId);
                            System.out.println("- Search results: " + results);
                        }
                        case "INDEX" -> {
                            gateway.index(query);
                            System.out.print("- URL sent for indexing.\n");
                        }
                    }
                    System.out.print("> ");
                }
            }
        } catch (NotBoundException | RemoteException e) {
            Debug.error("[CLIENT] Gateway not available: " + e.getMessage());
        }
    }
}
