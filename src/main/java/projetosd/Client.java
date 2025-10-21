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
            System.out.print("STATS: To see statistics\nEXIT: To exit the app\n\n> ");

            boolean run = true;
            Scanner scanner = new Scanner(System.in);
            while (run) {
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
            scanner.close();
        } catch (NotBoundException | RemoteException e) {
            Debug.error("[CLIENT] Gateway not available: " + e.getMessage());
        }
    }
}
