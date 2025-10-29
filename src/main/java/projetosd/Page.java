package projetosd;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Class representing page information including URL, title, and snippet.
 * 
 * @author Jose Amado & José Capinha
 * @version 1.0
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
     * Words found in the page
     */
    private final ArrayList<String> wordsFound;

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
        this.wordsFound = new ArrayList<>();
    }

    /**
     * Gets the words found in the page.
     * @return List of words found
     */
    public ArrayList<String> getWordsFound() {
        return new ArrayList<>(wordsFound);
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

    /**
     * Adds a list of words found into the class parameter
     * @param words ArrayList of words
     */
    public void InsertWordsFound(List<String> words) {
        this.wordsFound.addAll(words);
    }
}
