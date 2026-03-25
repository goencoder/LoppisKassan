package se.goencoder.loppiskassan.rest;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.logging.HttpLoggingInterceptor;
import se.goencoder.iloppis.invoker.ApiClient;
import se.goencoder.iloppis.invoker.ApiException;

/**
 * A fixed version of ApiClient that properly handles serialization with content types.
 * This overrides the problematic serialize() method that was causing the
 * "Content type null is not supported" error in iloppis-client 0.0.4.
 *
 * Authentication is handled implicitly via an OkHttp interceptor that reads
 * the current API key from {@link ApiHelper#getCurrentApiKey()} at request time.
 * This means all requests through the shared client automatically use the latest
 * API key without any header manipulation.
 */
public class FixedApiClient extends ApiClient {

    private static final Logger log = Logger.getLogger(FixedApiClient.class.getName());
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");
    private static final String HEARTBEAT_PATH = "/cashier-presence:heartbeat";
    private static final String STATS_LIVE_PATH = "/stats:live";
    private static final String EVENTS_FILTER_PATH = "/v1/events:filter";

    public FixedApiClient() {
        super();

        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(this::logHttpMessage);
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
        
        OkHttpClient client = getHttpClient().newBuilder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(new AuthInterceptor())
            .build();
        setHttpClient(client);
    }

    private void logHttpMessage(String message) {
        String normalized = message == null ? "" : message.toLowerCase(Locale.ROOT);
        boolean pollingEndpoint = normalized.contains(HEARTBEAT_PATH)
                || normalized.contains(STATS_LIVE_PATH)
                || normalized.contains(EVENTS_FILTER_PATH);

        if (message != null && message.startsWith("<-- ")) {
            int statusCode = parseStatusCode(message);
            if (statusCode >= 400) {
                log.warning("[HTTP] " + message);
                return;
            }
            if (pollingEndpoint && statusCode >= 200 && statusCode < 300) {
                log.fine("[HTTP] " + message);
                return;
            }
        } else if (pollingEndpoint) {
            log.fine("[HTTP] " + message);
            return;
        }

        log.log(pollingEndpoint ? Level.FINE : Level.INFO, "[HTTP] " + message);
    }

    private static int parseStatusCode(String message) {
        if (message == null || message.length() < 6 || !message.startsWith("<-- ")) {
            return -1;
        }
        int start = 4;
        int end = message.indexOf(' ', start);
        if (end <= start) {
            return -1;
        }
        try {
            return Integer.parseInt(message.substring(start, end));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /**
     * Override the serialize method to ensure content type is never null
     * and implement it directly without calling the problematic parent method
     */
    @Override
    public RequestBody serialize(Object obj, String contentType) throws ApiException {
        if (contentType == null || contentType.isEmpty()) {
            contentType = "application/json";
        }

        try {
            String json = getJSON().serialize(obj);
            MediaType mediaType = MediaType.get(contentType);
            return RequestBody.create(mediaType, json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ApiException("Failed to serialize object: " + e.getMessage(), e, 500, Collections.emptyMap());
        }
    }

    /**
     * Override selectHeaderContentType to ensure it never returns null
     */
    @Override
    public String selectHeaderContentType(String[] contentTypes) {
        String selectedContentType = super.selectHeaderContentType(contentTypes);
        if (selectedContentType == null || selectedContentType.isEmpty()) {
            return "application/json";
        }
        return selectedContentType;
    }

    @Override
    public <T> T handleResponse(okhttp3.Response response, java.lang.reflect.Type returnType) throws ApiException {
        AuthErrorHandler.handleAuthStatus(response.code());
        return super.handleResponse(response, returnType);
    }
}
