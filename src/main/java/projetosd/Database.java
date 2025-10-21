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
    private final String hostname;
    private final String port;
    private final String dbName;
    private final String username;
    private final String password;

    /**
     * Database Class Constructor
     *
     */
    public Database() {
        // Load .env
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();

        this.hostname = dotenv.get("DB_HOSTNAME");
        this.port = dotenv.get("DB_PORT");
        this.dbName = dotenv.get("DB_NAME");
        this.username = dotenv.get("DB_USERNAME");
        this.password = dotenv.get("DB_PASSWORD", "");
    }

    /**
     * Gets connection for database (barrel) instance
     *
     * @return DB (Barrel) Connection Object
     */
    public Connection getConnection(){
        Connection connection;

        try{
            String url = "jdbc:postgresql://" + this.hostname + ":" + this.port + "/" + this.dbName;

            connection = DriverManager.getConnection(url, this.username, this.password);

            return connection;
        }
        catch (SQLException e){
            Debug.error("[DATABASE] " + e.getMessage());
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
