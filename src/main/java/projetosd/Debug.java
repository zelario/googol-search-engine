package projetosd;

/**
 * Debug utility class.
 * Set DEBUG to false in production to disable all debug prints.
 * 
 * @author Jose Amado e José Capinha
 * @version 1.0
 */
public class Debug {
    /**
     * Global debug flag. Set to false to disable debug output.
     */
    public static boolean DEBUG = true; 

    /**
     * Prints a debug message if DEBUG is enabled.
     * @param message The message to print
     */
    public static void info(String message) {
        if (DEBUG) {
            System.out.println("[INFO] " + message);
        }
    }
    
    /**
     * Prints an error message 
     * @param message The error message to print
     */
    public static void error(String message) {
        if (DEBUG) {
            System.err.println("[ERROR] " + message);
        }
    }
    
    /**
     * Prints a warning message 
     * @param message The warning message to print
     */
    public static void warning(String message) {
        if (DEBUG) {
            System.out.println("[WARNING] " + message);
        }
    }
}
