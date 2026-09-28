package projetosd.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import projetosd.Log;

/**
 * Main class to start the web server application.
 * 
 * Based on Spring Boot framework.
 */
@SpringBootApplication(scanBasePackages = "projetosd")
public class Application {

    /**
     * Main method to launch the Spring Boot application.
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        var context = SpringApplication.run(Application.class, args);
        var environment = context.getEnvironment();
        String address = environment.getProperty("server.address", "127.0.0.1");
        Log.info("[WEB SERVER] Web server running on https://" + address);
    }
}