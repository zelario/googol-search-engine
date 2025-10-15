package projetosd;

import java.rmi.registry.LocateRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Gateway for user interaction.
 * Handles user input for adding URLs and searching keywords.
 * 
 * @author Jose Amado e José Capinha
 * @version 1.0
 */
public class Gateway {
    /**
     * Main for Gateway. Handles user input for adding URLs and searching keywords.
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        try {
            // No ip because localhost
            List<IndexServerInterface> barrels = findBarrels();
            IndexServerInterface currentBarrel;
            UrlQueueInterface queue = (UrlQueueInterface) LocateRegistry.getRegistry(1099).lookup("queue");

            try (Scanner scanner = new Scanner(System.in)) {
                String input;

                //  https://pt.wikipedia.org/wiki/Wikipédia:Página_principal

                System.out.println("End and Stats are reserved keywords with obvious functionalities\nAdd urls for indexing: Start with 'http'\nSearch for keywords: start with anything else\n");
                boolean run = true;
                while (run) {
                    currentBarrel = selectBarrel(barrels);
                    System.out.print("> ");
                    input = scanner.nextLine();

                    try {
                        switch (input) {
                            case "End", "end" -> run = false;

                            case "Stats", "stats" -> System.out.println(currentBarrel.printStats());

                            default -> {
                                if (input.startsWith("http")) {
                                    queue.addUrl(input, true);
                                    System.out.println("Added url: " + input);
                                } else {
                                    System.out.println(currentBarrel.searchWord(input));
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Finds active barrels.
     * @return List of active IndexServerInterface instances
     */
    private static List<IndexServerInterface> findBarrels() {
        List<IndexServerInterface> barrels = new ArrayList<>();
        int[] ports = {8183}; // Portas dos barrels
        
        for (int port : ports) {
            try {
                IndexServerInterface barrel = 
                    (IndexServerInterface) LocateRegistry
                        .getRegistry(port)
                        .lookup("index");
                barrels.add(barrel);
                Debug.info("Barrel on port: " + port);
            } catch (Exception e) {
                Debug.warning("No barrel on port: " + port);
            }
        }
        return barrels;
    }

    /**
     * Chooses a random barrel from the list. With failover.
     * @param barrels List of available barrels
     * @return A randomly selected IndexServerInterface
     */
    private static IndexServerInterface selectBarrel(List<IndexServerInterface> barrels) {
        List<IndexServerInterface> availableBarrels = new ArrayList<>(barrels);
        while (!availableBarrels.isEmpty()) {
            int idx = (int) (Math.random() * availableBarrels.size());
            IndexServerInterface barrel = availableBarrels.get(idx);
            try {
                barrel.ping();
                return barrel;
            } catch (Exception e) {
                availableBarrels.remove(idx);
            }
        }
        throw new RuntimeException("All Barrels failed!");
    }
}
