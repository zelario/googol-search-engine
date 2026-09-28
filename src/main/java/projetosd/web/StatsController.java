package projetosd.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;

import projetosd.Config;
import projetosd.Log;
import projetosd.Stats;

/**
 * Stats Controller to handle statistics page.
 * 
 * Manages the web interface for displaying system statistics.
 */
@Controller
public class StatsController {

    /**
     * Client Service for managing client services.
     */
    private final ClientService clientService;

    /**
     * Constructor for StatsController.
     * 
     * @param clientService The client service to be used.
     */
    public StatsController(ClientService clientService) {
        this.clientService = clientService;
    }

    /**
     * Handle GET requests for the stats page.
     * 
     * @param model The model to pass attributes to the view.
     * @return The stats page view.
     */
    @GetMapping("/stats")
    public String statsPage(@CookieValue("clientId") String clientId, Model model) {
        try{

            Stats stats = clientService.stats(clientId);
            model.addAttribute("topSearches", stats.getTopSearches());
            model.addAttribute("activeBarrels", stats.getActiveBarrels());
            model.addAttribute("responseTimes", stats.getAverageResponse());
            model.addAttribute("websocketHost", Config.WEBSOCKET_HOST);
            model.addAttribute("websocketPort", Config.WEBSOCKET_PORT);

        } catch (Exception e) {

            Log.error("[WEB SERVER] Failed to retrieve stats from gateway");
            model.addAttribute("message", "A connection or search error occurred. Please try again later.");
            return "errors/error";
        }
        
        return "stats";
    }
}
