package projetosd;

import io.github.cdimascio.dotenv.Dotenv;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Database Class for Barrels Managment and Connection
 *
 * @authors José Capinha & José Amado
 * @version 1.0
 */
public class Database {
    private String hostname;
    private String port;
    private String dbName;
    private String username;
    private String password;

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
        Connection connection = null;

        try{
            String url = "jdbc:postgresql://" + this.hostname + ":" + this.port + "/" + this.dbName;

            connection = DriverManager.getConnection(url, this.username, this.password);

            return connection;
        }
        catch (SQLException e){
            System.out.println("[DATABASE] Connection Error!");
            return null;
        }
    }

    // TODO: method to verify if all barrels are in the same state
    public void verifyStatus(){
        try{

        } catch (Exception e){
            System.out.println("[DATABASE] Could not guarantee same state for all barrels. Exiting...");
        }
    }
}
