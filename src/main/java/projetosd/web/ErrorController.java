package projetosd.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Custom Error Controller to handle errors.
 * 
 * Used to display user-friendly error pages.
 */
@Controller
public class ErrorController implements org.springframework.boot.web.servlet.error.ErrorController {

    /**
     * Handle error requests.
     * @param request The HTTP request.
     * @param model The model to pass attributes to the view.
     * @return The error view.
     */
    @RequestMapping("/error")
    public String handleError(HttpServletRequest request, Model model) {
        Object statusObj = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        if(statusObj != null){
            int statusCode = Integer.parseInt(statusObj.toString());

            if (statusCode == 404) {
                return "errors/404";
            }
            else if (statusCode >= 400 && statusCode < 500) {
                model.addAttribute("message", "Client error, something was wrong in the request");
            } else if (statusCode >= 500) {
                model.addAttribute("message", "Server error, something was wrong our side");
            }
        }

        return "errors/error";
    }
}
