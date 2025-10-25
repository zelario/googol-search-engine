package projetosd;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.Callable;

/**
 * Console for the Gateway RMI server.
 * 
 * @author Jose Amado & José Capinha
 * @version 1.0
 */
public class Client {

    /**
     * Client identifier for logging.
     */
    private String id = null;

    /**
     * Gateway remote interface.
     */
    private static GatewayInterface gateway = null;

    /**
     * 
     * @param args
     * 
     */
    public Client() {
        this.id = String.valueOf((int) ((System.currentTimeMillis() % 99) + 1));

        try {
            lookupGateway();
        } catch (Exception e) {
            Log.error("[CLIENT] Could not contact gateway: " + e.getMessage());
        }
    }

    /**
     * Main to run the client console.
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        Client client = new Client();

        if (gateway == null) {
            Log.error("[CLIENT " + client.id + "] Exiting due to no gateway connection.");
            return;
        }

        System.out.print("===== Welcome to Googol! =====\n\n");
            System.out.print("SEARCH: To search for a url\nINDEX: To add new url\nSTATS: To see statistics\nEXIT: To exit the app\n\n");

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
                    } else if (query.equals("STATS")) {
                        try {
                            callGateway(() -> { gateway.stats(client.id); return null; });
                        } catch (Exception e) {
                            Log.error("[CLIENT " + client.id + "] Stats failed after retries: " + e.getMessage());
                        }
                        continue;
                    } else if (query.equals("EXIT")) {
                        run = false;
                        System.out.println("=== Exiting. Goodbye! ===");
                    }

                    switch (mode) {
                        case "SEARCH" -> {
                            try {
                                List<List<Page>> results = callGateway(() -> gateway.search(client.id, query));
                                System.out.println("- Search results: " + results);
                            } catch (Exception e) {
                                Log.error("[CLIENT " + client.id + "] Search failed after retries: " + e.getMessage());
                            }
                        }
                        case "INDEX" -> {
                            try {
                                callGateway(() -> { gateway.index(query); return null; });
                                System.out.print("- URL sent for indexing.\n");
                            } catch (Exception e) {
                                Log.error("[CLIENT " + client.id + "] Index failed after retries: " + e.getMessage());
                            }
                        }
                    }
                    System.out.print("> ");
                }
            }
        
    }

    /**
     * Lookup the gateway with retry/backoff.
     */
    private static void lookupGateway() throws Exception {
        Exception exception = null;
        for (int attempt = 1; attempt <= Config.CLIENT_RETRIES; attempt++) {
            try {
                Registry registry = LocateRegistry.getRegistry(Config.GATEWAY_PORT);
                gateway = (GatewayInterface) registry.lookup("gateway");
                Log.info("[CLIENT] Connected to gateway on port " + Config.GATEWAY_PORT + " on attempt " + attempt);
                return;
            } catch (NotBoundException | RemoteException e) {
                exception = e;
                Log.warning("[CLIENT] Gateway lookup failed on attempt " + attempt + ": " + e.getMessage());
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
     */
    private static <T> T callGateway(Callable<T> action) throws Exception {
        Exception exception = null;
        for (int attempt = 1; attempt <= Config.CLIENT_RETRIES; attempt++) {
            try {
                if (gateway == null){
                    lookupGateway();
                }
                return action.call();
            } catch (RemoteException | NotBoundException e) {
                exception = e;
                Log.error("[CLIENT] Gateway call failed at attempt " + attempt + ": " + e.getMessage());
                try {
                    lookupGateway();
                } catch (Exception er) {
                    Log.error("[CLIENT] Re-lookup failed: " + er.getMessage());
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
}
