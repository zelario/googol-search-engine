package projetosd.web;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.springframework.stereotype.Service;

import projetosd.Config;
import projetosd.GatewayInterface;
import projetosd.Log;
import projetosd.Page;
import projetosd.Stats;

/**
 * Client Service class to interact with Gateway.
 * <p>
 * Represents the client-side service for communication with the Gateway server.
 */
@Service
public class ClientService {

    /**
     * Gateway interface.
     */
    private GatewayInterface gateway;

    /**
     * Default constructor for ClientService.
     * Initializes the gateway reference to null.
     */
    public ClientService() {
        gateway = null;
    }

    /**
     * Lookup the gateway with retry/backoff. Only used internally by callGateway.
     * @param id The client identifier.
     * @throws Exception if the gateway cannot be found after all retries.
     */
    @SuppressWarnings({"BusyWait"})
    private void lookupGateway(String id) throws Exception {
        Exception exception = null;
        for (int attempt = 1; attempt <= Config.CLIENT_RETRIES; attempt++) {
            try {
                Registry registry = LocateRegistry.getRegistry(Config.GATEWAY_HOST, Config.GATEWAY_PORT);
                gateway = (GatewayInterface) registry.lookup("gateway");
                Log.info("[CLIENT " + id + "] Connected to gateway on port " + Config.GATEWAY_PORT + " on attempt " + attempt);
                return;
            } catch (NotBoundException | RemoteException e) {
                exception = e;
                Log.warning("[CLIENT " + id + "] Gateway lookup failed on attempt " + attempt + ": " + e.getMessage());
                if (attempt == Config.CLIENT_RETRIES) break;
                try {
                    Thread.sleep((long) (Config.CLIENT_BACKOFF * Math.pow(2, attempt - 1)));
                } catch (InterruptedException er) {
                    break;
                }
            }
        }
        if (exception != null) {
            throw exception;
        } else {
            throw new Exception();
        }
    }

    /**
     * Execute a gateway call with automatic retries.
     * @param id The client identifier.
     * @param action The gateway call to execute.
     * @param <T> The return type of the gateway call.
     * @throws Exception if the gateway call fails after all retries.
     * @return The result of the gateway call.
     */
    @SuppressWarnings({"BusyWait"})
    private <T> T callGateway(String id, Callable<T> action) throws Exception {
        Exception exception = null;
        for (int attempt = 1; attempt <= Config.CLIENT_RETRIES; attempt++) {
            try {
                if (gateway == null) {
                    lookupGateway(id);
                }
                return action.call();
            } catch (RemoteException | NotBoundException e) {
                exception = e;
                Log.error("[CLIENT " + id + "] Gateway call failed at attempt " + attempt + ": " + e.getMessage());
            } catch (Exception e) {
                exception = e;
                break;
            }
        }
        
        for (int attempt = 1; attempt <= Config.CLIENT_RETRIES; attempt++) {
            try {
                Log.info("[CLIENT " + id + "] Retrying gateway lookup, attempt " + attempt);
                lookupGateway(id);
                return action.call();
            } catch (RemoteException | NotBoundException e) {
                exception = e;
                Log.error("[CLIENT " + id + "] Gateway retry call failed at attempt " + attempt + ": " + e.getMessage());
            } catch (Exception e) {
                exception = e;
                break;
            }
        }
        if (exception != null) {
            throw exception;
        } else {
            throw new Exception();
        }
    }

    /**
     * Submit a URL to be indexed.
     *
     * @param id The client identifier.
     * @param url The URL to index.
     * @throws Exception if the gateway call fails.
     */
    public void index(String id, String url) throws Exception {
        callGateway(id, () -> { gateway.index(id, url); return null; });
        Log.info("[CLIENT " + id + "] Indexed URL: " + url);
    }

    /**
     * Search for a query.
     * @param id The client identifier.
     * @param query The search query.
     * @param pageNumber The page number for pagination.
     * @param languageFilter The language filter to apply.
     * @param domainFilter The domain to restrict the search.
     * @return List of pages matching the search.
     * @throws Exception if the gateway call fails.
     */
    public List<Page> search(String id, String query, int pageNumber, Boolean languageFilter, String domainFilter) throws Exception {
        
        languageFilter = languageFilter != null && languageFilter;

        int filter;

        if (!languageFilter && (domainFilter == null || domainFilter.isEmpty())) {
            filter = 1;
        } else if (!languageFilter && !(domainFilter == null || domainFilter.isEmpty())) {
            filter = 2;
        } else if (languageFilter && (domainFilter == null || domainFilter.isEmpty())) {
            filter = 3;
        } else {
            filter = 4;
        }

        Log.info("[CLIENT " + id + "] Searching for query: '" + query + "' with filter " + filter + ", page number " + pageNumber);
        List<Page> results = callGateway(id, () -> gateway.search(id, query, pageNumber, filter, domainFilter));

        return (results != null) ? results : Collections.emptyList();
    }

    /**
     * Get backlinks for a page.
     * @param id The client identifier.
     * @param url The URL of the page.
     * @return List of pages linking to the given page.
     * @throws Exception if the gateway call fails.
     */
    public List<Page> backlinks(String id, String url) throws Exception {
        Page page = new Page(url, "", "");
        List<Page> backlinks = callGateway(id, () -> gateway.backlinks(id, page));
        Log.info("[CLIENT " + id + "] Retrieving backlinks for URL: " + url);
        return (backlinks != null) ? backlinks : Collections.emptyList();
    }

    /**
     * Get stats from the gateway.
     * @param id The client identifier.
     * @return Stats object containing gateway statistics.
     * @throws Exception if the gateway call fails.
     */
    public Stats stats(String id) throws Exception {
        Stats stats = callGateway(id, () -> gateway.stats(id));
        Log.info("[CLIENT " + id + "] Retrieved gateway stats");
        return stats;
    }
}