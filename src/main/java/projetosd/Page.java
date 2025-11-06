package projetosd;

import java.io.Serializable;

/**
 * Class representing page information including URL, title, and snippet.
 */
public class Page implements Serializable {

    /**
     * URL of the page
     */
    private final String url;

    /**
     * Title of the page
     */
    private final String title;

    /**
     * Snippet of the page
     */
    private final String snippet;

    /**
     * Constructs a Page object.
     * @param url URL of the page
     * @param title Title of the page
     * @param snippet Snippet of the page
     */
    public Page(String url, String title, String snippet) {
        this.url = url;
        this.title = title;
        this.snippet = snippet;
    }

    /**
     * Gets the URL of the page.
     * @return URL of the page
     */
    public String getUrl() {
        return url;
    }

    /**
     * Gets the title of the page.
     * @return Title of the page
     */
    public String getTitle() {
        return title;
    }

    /**
     * Gets the snippet of the page.
     * @return Snippet of the page
     */
    public String getSnippet() {
        return snippet;
    }
}
