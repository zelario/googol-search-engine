package projetosd;

import java.rmi.registry.LocateRegistry;
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
            IndexServerInterface index = (IndexServerInterface) LocateRegistry.getRegistry(8183).lookup("index");
            UrlQueueInterface queue = (UrlQueueInterface) LocateRegistry.getRegistry(1099).lookup("queue");

            try (Scanner scanner = new Scanner(System.in)) {
                String input;

                //  https://pt.wikipedia.org/wiki/Wikipédia:Página_principal

                System.out.println("End and Stats are reserved keywords with obvious functionalities\nAdd urls for indexing: Start with 'http'\nSearch for keywords: start with anything else\n");
                boolean run = true;
                while (run) {
                    System.out.print("> ");
                    input = scanner.nextLine();

                    try {
                        switch (input) {
                            case "End", "end" -> run = false;

                            case "Stats", "stats" -> System.out.println(index.printStats());

                            default -> {
                                if (input.startsWith("http")) {
                                    queue.addUrl(input, true);
                                    System.out.println("Added url: " + input);
                                } else {
                                    System.out.println(index.searchWord(input));
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println(e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
