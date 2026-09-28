package projetosd.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Footer Controller to handle footer links.
 * 
 * Manages requests for footer-related pages like contacts and terms of service.
 */
@Controller
public class FooterController {

    /**
     * Handle requests for the contacts page.
     * @return The contacts view.
     */
    @GetMapping("/contacts")
        public String getContacts() {
            return "information/contacts";
    }

    /**
     * Handle requests for the terms of service page.
     * @return The terms of service view.
     */
    @GetMapping("/terms")
        public String getTerms() {
            return "information/terms";
    }
}
