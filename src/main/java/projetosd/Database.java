package projetosd;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Database Class for Barrels Managment and Connection
 *
 * @authors José Capinha & José Amado
 * @version 1.0
 */
public class Database {
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
    public Database() {
        Dotenv dotenv = Dotenv.configure().directory("config/.env").load();

        this.hostname = dotenv.get("DB_HOSTNAME");
        this.port = dotenv.get("DB_PORT");
        this.dbName = dotenv.get("DB_NAME");
        this.username = dotenv.get("DB_USERNAME");
        this.password = dotenv.get("DB_PASSWORD");
    }

    /**
     * Gets connection for database instance
     *
     * @return DB Connection Object
     */
    public Connection getConnection(){
        Connection connection;

        try{
            String url = "jdbc:postgresql://" + this.hostname + ":" + this.port + "/" + this.dbName;

            connection = DriverManager.getConnection(url, this.username, this.password);

            return connection;
        }
        catch (SQLException e){
            Log.error("[DATABASE] " + e.getMessage());
            return null;
        }
    }

    // TODO: method to verify if all barrels are in the same state
    /*public void verifyStatus(){
        try{

        } catch (Exception e){
            System.out.println("[DATABASE] Could not guarantee same state for all barrels. Exiting...");
        }
    }*/
}
