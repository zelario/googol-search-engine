package projetosd.web;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import io.github.cdimascio.dotenv.Dotenv;
import projetosd.Log;
import projetosd.Page;

/**
 * Results Controller to handle search results and backlinks.
 * Manages the web interface for displaying search results and backlinks,
 * including integration with an external API for summarization.
 */
@Controller
public class ResultsController {

    /**
     * Client Registry for managing client services.
     */
    private final ClientService clientService;

    /**
     * Constructor for ResultsController.
     */
    public ResultsController(ClientService clientService) {
        this.clientService = clientService;
    }

    /**
     * Determine if the query is a question.
     * @param query The search query.
     * @return True if the query is a question, false otherwise.
     */
    private boolean isQuestion(String query) {
        if (query == null || query.isEmpty()) return false;
        query = query.trim().toLowerCase();
        List<String> questionWords = List.of("quem", "onde", "o que", "como", "quando", "porque", "qual"
            , "who", "where", "what", "how", "when", "why", "which", "whom", "whose", "did", "is", "are", "do", "does", "can", "could", "would", "will", "shall"); 
        return query.endsWith("?") || questionWords.stream().anyMatch(query::startsWith);
    }   

    /**
     * Call external Open API to summarize search results.
     * @param query The search query.
     * @param pages The list of search result pages.
     * @param isQuestion Indicates if the query is a question.
     * @return The summary response from the API.
     */
    private String callOpenApi(String query, List<Page> pages, boolean isQuestion) {
        if (query == null || query.isEmpty()) return "";

        Dotenv dotenv = Dotenv.configure().directory("config/.env").load();
        String token = dotenv.get("OPEN_ROUTER_KEY");

        String prompt;

        if (isQuestion) {

            prompt = """
                     You are a helpful assistant. Answer the user question directly in english. If it is not a factual question, respond with N/A.
                     Question:\s""" + query;

        } else {

            StringBuilder descriptions = new StringBuilder();
            if (pages != null) {
                for (Page page : pages) {
                    descriptions.append(page.getSnippet()).append("\n");
                }
            }
            prompt = """
                     You are a helpful assistant that summarizes search results concisely in english, focusing primarily on the search query.
                      Summarize the content:\s""" + descriptions;
        }

        String json = new JSONObject()
                .put("model", "openai/gpt-3.5-turbo")
                .put("messages", new JSONArray()
                        .put(new JSONObject()
                                .put("role", "system")
                                .put("content", prompt)))
                .put("max_tokens", 200)
                .put("temperature", 0.7)
                .toString();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpClient client = HttpClient.newHttpClient();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject jsonObject = new JSONObject(response.body());
            return jsonObject
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim();
        } catch (IOException | InterruptedException | JSONException e) {
            return "";
        }
    }

    /**
     * Get "Did You Mean" suggestion for a query using LanguageTool API.
     * @param query The search query.
     * @return The suggested correction or null if none.
     */
    private String getDidYouMean(String query) {
        if (query == null || query.isEmpty()) return null;

        try {
            HttpClient client = HttpClient.newHttpClient();
            String body = "language=en-US&text=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.languagetool.org/v2/check"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(5))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject responseJson = new JSONObject(response.body());
            JSONArray matches = responseJson.getJSONArray("matches");

            if (matches.isEmpty()) return null;

            StringBuilder corrected = new StringBuilder(query);

            // iterate backwards to replace offsets
            for (int i = matches.length() - 1; i >= 0; i--) {
                JSONObject match = matches.getJSONObject(i);
                JSONArray replacements = match.getJSONArray("replacements");

                if (!replacements.isEmpty()) {
                    String replacement = replacements.getJSONObject(0).getString("value");
                    int from = match.getInt("offset");
                    int to = from + match.getInt("length");
                    corrected.replace(from, to, replacement);
                }
            }

            if(corrected.toString().equalsIgnoreCase(query)) {
                return null;
            } else {
                return corrected.toString();
            }

        } catch (IOException | InterruptedException | JSONException e) {
            return null;
        }
    }

    /**
     * Handle GET requests for search results.
     * @param query The search query.
     * @param pageNumber The page number
     * @param model The model to pass attributes to the view.
     * @return The view to render.
     */
    @GetMapping("/results")
    public String getResults(@RequestParam(name = "q", required = false) String query,
                            @RequestParam(name = "pageNumber", required = false) Integer pageNumber,
                            @RequestParam(name = "force", required = false, defaultValue = "false") boolean force,
                            @CookieValue(value = "languageFilter", required = false) Boolean languageFilter,
                            @CookieValue(value = "domainFilter", required = false) String domainFilter,
                            @CookieValue("clientId") String clientId,
                            Model model) {

        if (pageNumber == null || pageNumber < 1) {
            pageNumber = 1;
        }

        try {
            if (query != null && !query.isEmpty()) {
                if (!force) {
                    String didYouMean = getDidYouMean(query);
                    if (didYouMean != null) {
                        model.addAttribute("didYouMean", didYouMean);
                        model.addAttribute("badQuery", query);
                        query = didYouMean;
                    }
                }

                List<Page> results = new ArrayList<>(clientService.search(clientId, query, pageNumber, languageFilter, domainFilter));
                model.addAttribute("results", results);

                boolean isQuestion = isQuestion(query);
                model.addAttribute("isQuestion", isQuestion);

                String openAIResponse = this.callOpenApi(query, results, isQuestion);
                model.addAttribute("overview", openAIResponse);
            }
        } catch (Exception e) {
            model.addAttribute("message", "A connection or search error occurred. Please try again later.");
            Log.error("[WEB SERVER] Could not retrieve search results from gateway");
            return "errors/error";
        }

        model.addAttribute("pageNumber", pageNumber);
        model.addAttribute("query", query);
        return "results";
    }

    /**
     * Handle POST requests to index Hacker News articles based on a query.
     * @param query The search query.
     * @param clientId The client identifier from cookies.
     * @return True if indexing was initiated successfully, false otherwise.
     */
    @PostMapping("/indexHackerNews")
    @ResponseBody
    @SuppressWarnings("CollectionsToArray")
    public boolean indexHackerNews(@RequestBody String query, @CookieValue("clientId") String clientId){
        if(query == null || query.isEmpty()) return false;

        HttpClient client = HttpClient.newHttpClient();

        try{
            // Initial request to get all ids
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://hacker-news.firebaseio.com/v0/topstories.json?print=pretty"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Regular array but is json
            JSONArray ids = new JSONArray(response.body());

            String cleanQuery = new JSONObject("{\"q\":" + query + "}").getString("q").toLowerCase();
            String[] words = cleanQuery.split(" ");

            ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            for (int i = 0; i < ids.length(); i++) {
                long id = ids.getLong(i);

                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try {
                        HttpRequest itemRequest = HttpRequest.newBuilder()
                                .uri(URI.create(String.format("https://hacker-news.firebaseio.com/v0/item/%s.json?print=pretty", id)))
                                .timeout(Duration.ofSeconds(5))
                                .build();

                        HttpResponse<String> itemResponse = client.send(itemRequest, HttpResponse.BodyHandlers.ofString());

                        if (itemResponse.body().equals("null")) return;

                        JSONObject item = new JSONObject(itemResponse.body());

                        if (item.optBoolean("deleted") || item.optBoolean("dead")) return;

                        String title = Jsoup.parse(item.optString("title", "")).text().toLowerCase();
                        String text = Jsoup.parse(item.optString("text", "")).text().toLowerCase();

                        boolean matches = Arrays.stream(words)
                                .allMatch(w -> title.contains(w) || text.contains(w));

                        if (!matches) return;

                        String url = item.optString("url", "");
                        if (url.isEmpty()) return;

                        clientService.index(clientId, url);

                    } catch (Exception ignored) {
                    }
                }, executor);

                futures.add(future);
            }

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            executor.shutdown();

            return true;
        } catch (IOException | InterruptedException | JSONException e){
            return false;
        }
    }

    /**
     * Handle GET requests for backlinks.
     * @param url The URL to find backlinks for.
     * @param clientId The client identifier from cookies.
     * @param model The model to pass attributes to the view.
     * @return The view to render.
     */
    @GetMapping("/backlinks")
    public String getBacklinks(@RequestParam(name = "b", required = false) String url, @CookieValue("clientId") String clientId, Model model){
        try {
            
            if(url == null || url.isEmpty()){
                model.addAttribute("url", null);
                model.addAttribute("results", null);
                return "results";
            }

            model.addAttribute("url", url);
            List<Page> backlinks =  clientService.backlinks(clientId, url);
            model.addAttribute("results", backlinks);

        } catch (Exception e) {
            model.addAttribute("message", "A connection or search error occurred. Please try again later.");
            Log.error("[WEB SERVER] Could not retrieve backlinks from gateway");
            return "error";
        }
        
        return "results";
    }
}