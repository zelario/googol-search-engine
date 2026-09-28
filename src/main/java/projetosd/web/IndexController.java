package projetosd.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import projetosd.Log;

/**
 * Index Controller to handle indexing requests.
 *
 * Manages the web interface for submitting URLs to be indexed.
 */
@Controller
public class IndexController {

    /**
     * Client Registry for managing client services.
     */
    private final ClientService clientService;

    /**
     * Constructor for IndexController.
     * @param clientService The client service to interact with the gateway.
     */
    public IndexController(ClientService clientService) {
        this.clientService = clientService;
    }

    /**
     * Handle GET requests for the index page.
     * @return The index view.
     */
    @GetMapping("/index")
    public String indexPage() {
        return "index";
    }

    /**
     * Handle POST requests to index a URL.
     * @param url The URL to be indexed.
     * @param clientId The client identifier from the cookie.
     * @return The index view.
     */
    @PostMapping("/index")
    public String index(@RequestParam("url") String url, @CookieValue("clientId") String clientId, Model model) {
        try{
            clientService.index(clientId, url);
        } catch (Exception e) {
            Log.error("[WEB SERVER] Failed to send URL to gateway for indexing");
            model.addAttribute("message", "A connection or search error occurred. Please try again later.");
            return "errors/error";
        }
        return "index";
    }
}
