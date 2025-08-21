package se.goencoder.loppiskassan.rest;

import java.util.Collections;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import se.goencoder.iloppis.invoker.ApiClient;
import se.goencoder.iloppis.invoker.ApiException;
import java.nio.charset.StandardCharsets;

/**
 * A fixed version of ApiClient that properly handles serialization with content types.
 * This overrides the problematic serialize() method that was causing the
 * "Content type null is not supported" error in iloppis-client 0.0.4.
 */
public class FixedApiClient extends ApiClient {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");

    /**
     * Override the serialize method to ensure content type is never null
     * and implement it directly without calling the problematic parent method
     */
    @Override
    public RequestBody serialize(Object obj, String contentType) throws ApiException {
        // If contentType is null or empty, use application/json
        if (contentType == null || contentType.isEmpty()) {
            contentType = "application/json";
        }

        try {
            // Serialize the object to JSON using the same JSON serializer as the parent
            String json = getJSON().serialize(obj);

            // Create the RequestBody with the correct parameter order for OkHttp 3.x
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
        // If nothing was selected, default to application/json
        if (selectedContentType == null || selectedContentType.isEmpty()) {
            return "application/json";
        }
        return selectedContentType;
    }

    /**
     * Helper method to directly create a JSON request body
     */
    public RequestBody createJsonRequestBody(Object obj) throws ApiException {
        try {
            String json = getJSON().serialize(obj);
            return RequestBody.create(JSON_MEDIA_TYPE, json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            // Using more meaningful error code (500) and empty map instead of null for headers
            throw new ApiException("Failed to serialize object to JSON: " + e.getMessage(), e, 500, Collections.emptyMap());
        }
    }
}
