package projetosd;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * Console for the Gateway RMI server.
 * 
 * @author Jose Amado & Joao Capinha
 * @version 1.0
 */
public class Client {

    /**
     * Client identifier for logging.
     */
    private String id;

    /**
     * Gateway remote interface.
     */
    private GatewayInterface gateway;

    /**
     * Current page number for search pagination.
     */
    private static int pageNumber;

    /**
     * 
     * @param args
     * 
     */
    public Client() {
        id = String.valueOf((int) ((System.currentTimeMillis() % 99) + 1));

        try {
            lookupGateway();
        } catch (Exception e) {
            gateway = null;
            Log.error("[CLIENT] Could not contact gateway: " + e.getMessage());
        }
    }

    /**
     * Lookup the gateway with retry/backoff.
     */
    private void lookupGateway() throws Exception {
        Exception exception = null;
        for (int attempt = 1; attempt <= Config.CLIENT_RETRIES; attempt++) {
            try {
                Registry registry = LocateRegistry.getRegistry(Config.GATEWAY_PORT);
                gateway = (GatewayInterface) registry.lookup("gateway");
                Log.info("[CLIENT " + id + "] Connected to gateway on port " + Config.GATEWAY_PORT + " on attempt " + attempt);
                return;
            } catch (NotBoundException | RemoteException e) {
                exception = e;
                Log.warning("[CLIENT " + id + "] Gateway lookup failed on attempt " + attempt + ": " + e.getMessage());
                if (attempt == Config.CLIENT_RETRIES) break;
                try {
                    Thread.sleep((long) (Config.CLIENT_BACKOFF * Math.pow(2, attempt - 1)));
                } catch (InterruptedException er) {
                    break;
                }
            }
        }
        throw exception;
    }

    /**
     * Execute a gateway call with automatic retries. If an exception occurs the client will try to re-lookup the gateway and retry the call.
     * @param <T> The return type of the callable action.
     * @param action The callable action to execute.
     */
    private <T> T callGateway(Callable<T> action) throws Exception {
        Exception exception = null;
        for (int attempt = 1; attempt <= Config.CLIENT_RETRIES; attempt++) {
            try {
                if (gateway == null){
                    lookupGateway();
                }
                return action.call();
            } catch (RemoteException | NotBoundException e) {
                exception = e;
                Log.error("[CLIENT " + id + "] Gateway call failed at attempt " + attempt + ": " + e.getMessage());
                try {
                    lookupGateway();
                } catch (Exception er) {
                    Log.error("[CLIENT " + id + "] Re-lookup failed: " + er.getMessage());
                }
                if (attempt == Config.CLIENT_RETRIES) break;
                try {
                    Thread.sleep((long) (Config.CLIENT_BACKOFF * Math.pow(2, attempt - 1)));
                } catch (InterruptedException err) {
                    break;
                }
            }
        }
        if (exception != null) throw exception;
        throw new Exception("Gateway call failed after retries");
    }

    /**
     * Main to run the client console.
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        Client client = new Client();

        if (client.gateway == null) {
            Log.error("[CLIENT " + client.id + "] Exiting due to no gateway connection.");
            return;
        }
        
        System.out.print("===== Welcome to Googol! You are " + client.id + "! =====\n\n");
            System.out.print("SEARCH: To search for a url\nINDEX: To add new url\nSTATS: To see statistics\nEXIT: To exit the app\n");

            boolean run = true;
            String mode = "SEARCH";

            try (Scanner scanner = new Scanner(System.in)) {

                System.out.print("\n>  ");

                while (run) {
                    String query = scanner.nextLine().trim();

                    if (!mode.equals("SEARCH") && (query.equals("SEARCH") || query.equals("search"))) {
                        mode = "SEARCH";
                        System.out.print("\n=== SEARCH MODE ===\n\n> ");
                        continue;
                    } else if (!mode.equals("INDEX") && (query.equals("INDEX") || query.equals("index"))) {
                        mode = "INDEX";
                        System.out.print("\n=== INDEX MODE ===\n\n> ");
                        continue;
                    } else if (query.equals("STATS") || query.equals("stats")) {
                        try {
                            String stats = client.callGateway(() -> client.gateway.stats(client.id));
                            System.out.print("\n" + stats + "\n> ");
                        } catch (Exception e) {
                            Log.error("[CLIENT " + client.id + "] Stats failed after retries: " + e.getMessage());
                        }
                        continue;
                    } else if (query.equals("EXIT") || query.equals("exit")) {
                        run = false;
                        System.out.println("=== Exiting. Goodbye! ===");
                    }

                    switch (mode) {
                        case "SEARCH" -> {
                            try {
                                pageNumber = 1;
                                System.out.print("\n=== Search Results ===\n");
                                while(true){
                                    String results = client.callGateway(() -> client.gateway.search(client.id, query, pageNumber));
                                    System.out.println(results);
                                    System.out.print("                 Prev             End              Next\n\n> ");
                                    String command = scanner.nextLine().trim();
                                    if(command.equalsIgnoreCase("end")){
                                        System.out.print("\n=== Ending of search results ===\n\n");
                                        break;
                                    } else if(command.equalsIgnoreCase("next")){
                                        pageNumber++;
                                    } else if(command.equalsIgnoreCase("prev")){
                                        pageNumber--;
                                    } else {
                                        System.out.print("\nInvalid command.\n ");
                                    }
                                }
                            } catch (Exception e) {
                                Log.error("[CLIENT " + client.id + "] Search failed after retries: " + e.getMessage());
                            }
                        }
                        case "INDEX" -> {
                            try {
                                client.callGateway(() -> { client.gateway.index(query); return null; });
                                System.out.print("\n- URL sent for indexing.\n\n");
                            } catch (Exception e) {
                                Log.error("[CLIENT " + client.id + "] Index failed after retries: " + e.getMessage());
                            }
                        }
                    }
                    System.out.print("> ");
                }
            }
    }
}
