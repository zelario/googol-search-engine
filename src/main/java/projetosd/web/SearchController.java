package projetosd.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Search Controller to handle search page and redirection.
 * 
 * Manages the web interface for the search page and redirects to results.
 */
@Controller
public class SearchController {
    
    /**
     * Handle GET requests for the root path and redirect to search page.
     * @return The redirect instruction to the search page.
     */
    @GetMapping("/")
    public String redirectToSearch() {
        return "redirect:/search";
    }

    /**
     * Handle GET requests for the search page.
     * @return The search page view.
     */
    @GetMapping("/search")
    public String searchPage() {
        return "search"; 
    }

    /**
     * Handle POST requests for search submissions and redirect to results.
     * @param query The search query.
     * @param redirectAttributes The redirect attributes to pass parameters.
     * @return The redirect instruction to the results page.
     */
    @PostMapping("/search")
    public String search(@RequestParam String query, RedirectAttributes redirectAttributes) {
        redirectAttributes.addAttribute("q", query);
        return "redirect:/results";
    }
}
