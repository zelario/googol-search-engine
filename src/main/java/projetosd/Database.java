package projetosd;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Database Class for Barrels Management and Connection
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
     * @param barrelPort DB barrel port
     */
    @SuppressWarnings("OverridableMethodCallInConstructor")
    public Database(int barrelPort) {
        if (dotenv == null) dotenv = Dotenv.configure().directory("config/.env").load();

        this.hostname = dotenv.get("DB_HOSTNAME");
        this.port = dotenv.get("DB_PORT");
        this.dbName = "Barrel" + barrelPort;
        this.username = dotenv.get("DB_USERNAME");
        this.password = dotenv.get("DB_PASSWORD");

        try {
            ensureDatabaseExists(barrelPort);
        } catch (SQLException e) {
            Log.error("[DATABASE] Error ensuring database exists: " + e.getMessage());
        }
    }

    /**
     * Gets connection for database instance
     *
     * @param dbName Database name (if null, uses instance dbName)
     * @return DB Connection Object
     */
    public Connection getConnection(String dbName) {
        Connection connection;

        try {
            if (dbName == null) {
                dbName = this.dbName;
            }
            String url = "jdbc:postgresql://" + this.hostname + ":" + this.port + "/" + dbName;

            connection = DriverManager.getConnection(url, this.username, this.password);

            return connection;
        } catch (SQLException e) {
            return null;
        }
    }

    /**
     * Ensures the target database exists for this barrel. If it does not exist,
     * attempts to create it by connecting to the default 'postgres' database.
     * 
     * @param barrelPort The port number of the barrel database.
     */
    public void ensureDatabaseExists(int barrelPort) throws SQLException {

        try (Connection ignored = getConnection(this.dbName)) {
            if (ignored != null) {
                return;
            }
        } catch (SQLException e) {}

        Log.warning("[BARREL " + barrelPort + "] Unable to connect to barrel database. Attempting to create it.");

        try (Connection adminConn = getConnection("postgres")) {

            final String createSql = "CREATE DATABASE \"" + this.dbName + "\" WITH ENCODING 'UTF8' TEMPLATE template1";
            try (Statement st = adminConn.createStatement()) {
                st.executeUpdate(createSql);
                Log.info("[BARREL " + barrelPort + "] Database '" + this.dbName + "' created successfully.");
            }

            try (Connection barrelConn = getConnection(this.dbName)) {

                String sql = Files.readString(Paths.get("scripts/create_tables.sql"));
                try (Statement stmt = barrelConn.createStatement()) {
                    stmt.execute(sql);
                    Log.info("[BARREL " + barrelPort + "] Tables created successfully in barrel database.");
                } catch (SQLException e) {
                    Log.error("[BARREL " + barrelPort + "] Failed to execute create_tables.sql on barrel database");
                }


                sql = Files.readString(Paths.get("scripts/stop_words.sql"));
                try (Statement stmt = barrelConn.createStatement()) {
                    stmt.execute(sql);
                    Log.info("[BARREL " + barrelPort + "] Stop words procedure created successfully in barrel database.");
                } catch (SQLException e) {
                    Log.error("[BARREL " + barrelPort + "] Failed to execute stop_words.sql on barrel database");
                }

                Log.info("[BARREL " + barrelPort + "] Tables created successfully in barrel database.");

            } catch (SQLException e) {
                Log.error("[BARREL " + barrelPort + "] Failed to connect to newly created barrel database");
            }

        } catch (SQLException | IOException e) {
            Log.error("[BARREL " + barrelPort + "] ensureDatabaseExists error: " + e.getMessage());
        }
    }
}
