package projetosd;

import java.io.Serializable;
import java.util.ArrayList;

/**
 * Class representing page information including URL, title, and snippet.
 * 
 * @author Jose Amado e José Capinha
 * @version 1.0
 */
public class Page implements Serializable {

    private final String url;
    private final String title;
    private final String snippet;
    private final ArrayList<String> wordsFound;

    public Page(String url, String title, String snippet) {
        this.url = url;
        this.title = title;
        this.snippet = snippet;
        this.wordsFound = new ArrayList<>();
    }

    public ArrayList<String> getWordsFound() {
        return new ArrayList<>(wordsFound);
    }

    public String getUrl() {
        return url;
    }

    public String getTitle() {
        return title;
    }

    public String getSnippet() {
        return snippet;
    }

    @Override
    public String toString() {
        return "Page{" + "title='" + title + '\'' + ", url='" + url + '\'' + ", snippet='" + snippet + '\'' + ", wordsFound=" + wordsFound + '}';
    }
}
