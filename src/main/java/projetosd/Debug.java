package projetosd;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Debug utility class.
 * Set DEBUG to false in production to disable all debug prints.
 * 
 * @author Jose Amado & José Capinha
 * @version 1.0
 */
public class Debug {
    
    /**
     * Log file path.
     */
    private static final String LOG_FILE = "logs/Googol.log";
    
    /**
     * Date/time formatter.
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Writes a message to the log file.
     * @param type The log type (INFO, ERROR, WARNING)
     * @param message The message to log
     */
    private static synchronized void writeToLog(String type, String message) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(LOG_FILE, true))) {
            String timestamp = LocalDateTime.now().format(DATE_FORMATTER);
            writer.println(String.format("[%s] [%s] %s", timestamp, type, message));
        } catch (IOException e) {
            System.err.println("[LOG ERROR] Failed to write to log file: " + e.getMessage());
        }
    }

    /**
     * Clears the log file.
     */
    public static void clearLog() {
        try (FileWriter fw = new FileWriter(LOG_FILE, false)) {
            fw.write("");
        } catch (IOException e) {}
    }

    /**
     * Prints a debug message if DEBUG is enabled.
     * @param message The message to print
     */
    public static void info(String message) {
        if (Config.DEBUG) {
            System.out.println("[INFO] " + message);
        }
        writeToLog("INFO", message);
    }
    
    /**
     * Prints an error message 
     * @param message The error message to print
     */
    public static void error(String message) {
        if (Config.DEBUG) {
            System.err.println("[ERROR] " + message);
        }
        writeToLog("ERROR", message);
    }

    /**
     * Prints a warning message 
     * @param message The warning message to print
     */
    public static void warning(String message) {
        if (Config.DEBUG) {
            System.out.println("[WARNING] " + message);
        }
        writeToLog("WARNING", message);
    }

    public static void url(String message) {
        if (Config.DEBUG) {
            System.out.println("[URL] " + message);
        }
    }
}
