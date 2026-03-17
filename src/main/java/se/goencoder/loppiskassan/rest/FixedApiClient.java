package se.goencoder.loppiskassan.rest;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
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

    public FixedApiClient() {
        super();

        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor(message ->
                log.info("[HTTP] " + message)
        );
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
        
        OkHttpClient client = getHttpClient().newBuilder()
            .addInterceptor(loggingInterceptor)
            .addInterceptor(new AuthInterceptor())
            .build();
        setHttpClient(client);
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
