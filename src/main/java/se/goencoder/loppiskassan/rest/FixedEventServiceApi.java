package se.goencoder.loppiskassan.rest;

import okhttp3.Call;
import okhttp3.Request;
import se.goencoder.iloppis.api.EventServiceApi;
import se.goencoder.iloppis.invoker.ApiCallback;
import se.goencoder.iloppis.invoker.ApiClient;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.FilterEventsRequest;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Custom extension of EventServiceApi that fixes the content type issues
 * present in iloppis-client 0.0.4
 */
public class FixedEventServiceApi extends EventServiceApi {

    private final ApiClient apiClient;

    public FixedEventServiceApi(ApiClient apiClient) {
        super(apiClient);
        this.apiClient = apiClient;
    }

    /**
     * Override the problematic method to explicitly set the content type
     * This implementation avoids modifying global headers which could cause issues in concurrent environments
     */
    @Override
    public Call eventServiceFilterEventsCall(FilterEventsRequest body, ApiCallback _callback) throws ApiException {
        // Create a local copy of headers for this specific request
        Map<String, String> localHeaderParams = new HashMap<>();
        localHeaderParams.put("Content-Type", "application/json");

        try {
            // Get the base path to use
            String basePath;
            Method getBasePathMethod = ApiClient.class.getDeclaredMethod("getBasePath");
            getBasePathMethod.setAccessible(true);
            basePath = (String) getBasePathMethod.invoke(apiClient);

            // Build the request with our custom headers
            String localVarPath = "/v1/events:filter";
            Request request = apiClient.buildRequest(
                    basePath,
                    localVarPath,
                    "POST",
                    null,  // queryParams
                    null,  // collectionQueryParams
                    body,  // body
                    localHeaderParams,  // Our custom headers
                    new HashMap<>(),  // cookieParams
                    new HashMap<>(),  // formParams
                    new String[0],  // authNames
                    _callback
            );

            // Create the call from the request
            return apiClient.getHttpClient().newCall(request);
        } catch (Exception e) {
            // Create an ApiException with a valid constructor signature
            throw new ApiException("Failed to build request with custom headers: " + e.getMessage(), e, 0, null);
        }
    }
}
