package bep.hax.util.commands;

import bep.hax.modules.Api2b2t;
import bep.hax.util.LogUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import org.jetbrains.annotations.Nullable;

public class ApiHandler {
    public static final String API_2B2T_URL = "https://api.2b2t.vc";
    private static final String USER_AGENT = "Bephax/1.0";
    private static final long MIN_REQUEST_DELAY_MS = 1000L;
    private static long lastRequestTime = 0L;
    private static final ReentrantLock rateLimitLock = new ReentrantLock();
    private static final long CACHE_DURATION_MS = 30000L;
    private static final Cache<String, String> responseCache = Caffeine.newBuilder().expireAfterWrite(Duration.ofMillis(30000L)).maximumSize(256L).build();
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_RETRY_DELAY_MS = 2000L;

    public static void sendResponse(String message) {
        Api2b2t.sendMessage(message);
    }

    public static void sendResponse(String message, String playerName) {
        Api2b2t.sendMessage(message, playerName);
    }

    public static void sendErrorResponse() {
        Api2b2t.sendError("An error occurred, please try again later or check latest.log for more info.");
    }

    public static void sendRateLimitError() {
        Api2b2t.sendError("§cAPI rate limit reached. Please wait a moment before trying again.");
    }

    public static void sendNotFoundResponse(String playerName) {
        Api2b2t.sendError("Player §c" + playerName + " §4not found.");
    }

    @Nullable
    public String fetchResponse(String requestString) {
        String cached = responseCache.getIfPresent(requestString);
        if (cached != null) {
            LogUtil.info("Using cached response for: " + requestString, "ApiHandler");
            return cached;
        }

        rateLimitLock.lock();

        try {
            long now = System.currentTimeMillis();
            long timeSinceLastRequest = now - lastRequestTime;
            if (timeSinceLastRequest < 1000L) {
                long sleepTime = 1000L - timeSinceLastRequest;
                LogUtil.info("Rate limiting: waiting " + sleepTime + "ms", "ApiHandler");
                Thread.sleep(sleepTime);
            }

            lastRequestTime = System.currentTimeMillis();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            rateLimitLock.unlock();
        }

        String response = this.fetchWithRetry(requestString);
        if (response != null && !response.equals("204 Undocumented")) {
            responseCache.put(requestString, response);
        }

        return response;
    }

    @Nullable
    private String fetchWithRetry(String requestString) {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest req;
        try {
            req = HttpRequest.newBuilder()
                .uri(new URI(requestString))
                .header("Accept", "application/json")
                .header("User-Agent", "Bephax/1.0")
                .GET()
                .timeout(Duration.ofSeconds(30L))
                .build();
        } catch (URISyntaxException err) {
            sendErrorResponse();
            LogUtil.error(err.toString(), "ApiHandler");
            return null;
        }

        if (req == null) {
            sendErrorResponse();
            return null;
        }

        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                HttpResponse<String> res = client.sendAsync(req, BodyHandlers.ofString()).get();
                if (res == null) {
                    sendErrorResponse();
                    return null;
                }

                if (res.statusCode() == 200) {
                    return res.body();
                }

                if (res.statusCode() == 204) {
                    return "204 Undocumented";
                }

                if (res.statusCode() == 429) {
                    sendRateLimitError();
                    LogUtil.warn("Rate limited (429). Not retrying to avoid making it worse.", "ApiHandler");
                    return null;
                }

                sendErrorResponse();
                LogUtil.warn("Received unexpected response from api.2b2t.vc: \"" + res.statusCode() + " - " + res.body() + "\"", "ApiHandler");
                return null;
            } catch (Exception err) {
                if (attempt >= 2) {
                    sendErrorResponse();
                    LogUtil.error(err.toString(), "ApiHandler");
                    return null;
                }

                LogUtil.warn("Request failed, retrying... (" + (attempt + 1) + "/3): " + err.getMessage(), "ApiHandler");

                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    sendErrorResponse();
                    return null;
                }
            }
        }

        return null;
    }
}
