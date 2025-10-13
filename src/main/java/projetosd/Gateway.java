package projetosd;

import java.rmi.registry.*;
import java.util.*;

public class Gateway {
    public static void main(String[] args){
        try{
            // No ip because localhost
            Index index = (Index) LocateRegistry.getRegistry(8183).lookup("index");
            Scanner sc = new Scanner(System.in);

            String input;

            //  https://pt.wikipedia.org/wiki/Wikipédia:Página_principal

            System.out.println("End and Stats are reserved keywords with obvious functionalities\nAdd urls for indexing: Start with 'http'\nSearch for keywords: start with anything else\n");
            boolean run = true;
            while(run){
                System.out.print("> ");
                input = sc.nextLine();

                try{
                    switch (input) {
                        case "End", "end":
                            run = false;
                            break;

                        case "Stats", "stats":
                            System.out.println(index.printStats());
                            break;

                        default:
                            if(input.startsWith("http")){
                                index.putNew(input);
                                System.out.println("Added url: " + input);
                            }
                            else System.out.println(index.searchWord(input));
                    }

                } catch (Exception e){
                    System.out.println(e.getMessage());
                    continue;
                }
            }
            sc.close();
        } catch (Exception e){
            e.printStackTrace();
        }
    }
}
