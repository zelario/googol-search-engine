package projetosd;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Database Class for Barrels Management and Connection
 *
 * @authors José Capinha & José Amado
 * @version 1.0
 */
public class Database {
    /**
     * Static attribute shared by all instances of the Dotenv class
     */
    private static Dotenv dotenv;

    /**
     * Hostname of the database server
     */
    private final String hostname;

    /**
     * Port of the database server
     */
    private final String port;

    /**
     * Name of the database
     */
    private final String dbName;

    /**
     * Username for the database
     */
    private final String username;

    /**
     * Password for the database
     */
    private final String password;

    /**
     * Database Class Constructor
     */
    public Database(int identifier) {
        if (dotenv == null) dotenv = Dotenv.configure().directory("config/.env").load();

        String placeholder = "DB" + identifier + "_";

        this.hostname = dotenv.get(placeholder + "HOSTNAME");
        this.port = dotenv.get(placeholder + "PORT");
        this.dbName = dotenv.get(placeholder + "NAME");
        this.username = dotenv.get(placeholder + "USERNAME");
        this.password = dotenv.get(placeholder + "PASSWORD");
    }

    /**
     * Gets connection for database instance
     *
     * @return DB Connection Object
     */
    public Connection getConnection() {
        Connection connection;

        try {
            String url = "jdbc:postgresql://" + this.hostname + ":" + this.port + "/" + this.dbName;

            connection = DriverManager.getConnection(url, this.username, this.password);

            return connection;
        } catch (SQLException e) {
            Log.error("[DATABASE] " + e.getMessage());
            return null;
        }
    }

    /**
     * Method to count the number of databases in the system
     *
     * @return Database count
     */
    public static int databaseCount() {
        if (dotenv == null) Dotenv.configure().directory("config/.env").load();

        String count = dotenv.get("DB_COUNT");

        if (count == null) {
            Log.warning("[DATABASE] Database count not set");
            return 0;
        }

        try {
            return Integer.parseInt(count);
        } catch (NumberFormatException e) {
            Log.error("[DATABASE] Error converting database count to integer");
            return 0;
        }
    }
}
