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
 * @author Jose Amado & Joao Capinha
 * @version 1.0
 */
public class Client {

    /**
     * Client identifier for logging.
     */
    @SuppressWarnings("FieldMayBeFinal")
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
     * Constructs the Client object.
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

    //---------------------------------- RESULT FORMATTING METHODS --------------------------------------------//

    /**
     * Print search results to console.
     * @param results List of pages to print
     */
    private void printResults(List<Page> results){
        StringBuilder sb = new StringBuilder();
        int startIndex = (pageNumber - 1) * 10 + 1;
        sb.append(String.format("\n- Page %d:\n\n", pageNumber));

        if (results == null || results.isEmpty()) {
            System.out.print("\nNo results found.\n");
        } else {
            for (int i = 0; i < results.size(); i++) {
                Page p = results.get(i);
                int num = startIndex + i;
                sb.append(String.format("%d) %s\n", num, p.getTitle() == null || p.getTitle().isBlank() ? "(no title)" : p.getTitle()));
                sb.append(String.format("   %s\n", p.getUrl()));
                String snippet = p.getSnippet() == null ? "" : p.getSnippet();
                if (!snippet.isBlank()) {
                    sb.append(String.format("   \"%s\"\n", snippet.length() > 200 ? snippet.substring(0, 200) + "..." : snippet));
                }
            }
            System.out.print(sb);
        }
    }

    /**
     * Print backlinks of a selected page to console.
     * @param selectedPage The page for which backlinks are shown
     * @param backlinks List of backlink pages
     */
    private void printBacklinks(Page selectedPage, List<Page> backlinks){
        int limit = 10;
        StringBuilder sb = new StringBuilder();
        sb.append("\n=== Backlinks for: ").append(selectedPage.getUrl()).append(" ===\n\n");

        if (backlinks == null || backlinks.isEmpty()) {
            sb.append("No backlinks found.\n");
        } else {
            int i = 1;
            for (Page p : backlinks ) {
                sb.append(String.format("%d) %s\n", i++, p.getUrl()));
                if (i > limit) break;
            }
        }
        System.out.print(sb);
    }

    //---------------------------------- END OF RESULT FORMATTING METHODS ------------------------------------------//

    //------------------------------------ GATEWAY CONNECTION METHODS ------------------------------------------//

    /**
     * Lookup the gateway with retry/backoff.
     */
    @SuppressWarnings({"BusyWait", ""})
    private void lookupGateway() throws Exception {
        Exception exception = null;
        for (int attempt = 1; attempt <= Config.CLIENT_RETRIES; attempt++) {
            try {
                Registry registry = LocateRegistry.getRegistry(Config.GATEWAY_HOST, Config.GATEWAY_PORT);
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

        // This only works for debug, it avoids the warning
        assert exception != null;
        throw exception;
    }

    /**
     * Execute a gateway call with automatic retries. If an exception occurs the client will try to re-lookup the gateway and retry the call.
     * @param <T> The return type of the callable action.
     * @param action The callable action to execute.
     */
    @SuppressWarnings("BusyWait")
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

    //----------------------------------- END OF GATEWAY CONNECTION METHODS -----------------------------------//

    /**
     * Main to run the client console.
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        Client client = new Client();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Log.info("[CLIENT " + client.id + "] Exiting client.");
        }));

        if (client.gateway == null) {
            Log.error("[CLIENT " + client.id + "] Exiting due to no gateway connection");
            return;
        }
        
        System.out.print("===== Welcome to Googol! You are client " + client.id + "! =====\n\n");
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
                                    List<Page> results = client.callGateway(() -> client.gateway.search(client.id, query, pageNumber));
                                    client.printResults(results);
                                    System.out.print("\n                 Prev             Backlinks              Next\n\n> ");
                                    String command = scanner.nextLine().trim();
                                    if (command.equalsIgnoreCase("next")) {
                                        pageNumber++;
                                    } else if (command.equalsIgnoreCase("prev")) {
                                        pageNumber--;
                                    } else {
                                        try {
                                            int selection = Integer.parseInt(command);
                                            int startIndex = (pageNumber - 1) * 10 + 1;
                                            int endIndex = startIndex + (results == null ? 0 : results.size()) - 1;
                                            if (results != null && selection >= startIndex && selection <= endIndex) {
                                                int localIndex = selection - startIndex;
                                                Page selectedPage = results.get(localIndex);
                                                List<Page> backlinks = client.callGateway(() -> client.gateway.backlinks(client.id, selectedPage));
                                                client.printBacklinks(selectedPage, backlinks);
                                                System.out.print("\n=== Ending of search results ===\n\n");
                                                break;
                                            }
                                        } catch (NumberFormatException nfe) {
                                            System.out.print("\n=== Ending of search results ===\n\n");
                                            break;
                                        }
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