package se.goencoder.loppiskassan.rest;

import okhttp3.Call;
import se.goencoder.iloppis.api.EventServiceApi;
import se.goencoder.iloppis.invoker.ApiCallback;
import se.goencoder.iloppis.invoker.ApiClient;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.FilterEventsRequest;

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
     */
    @Override
    public Call eventServiceFilterEventsCall(FilterEventsRequest body, ApiCallback _callback) throws ApiException {
        // Add a default content type header to ensure serialization works properly
        apiClient.addDefaultHeader("Content-Type", "application/json");

        // Call the parent implementation
        return super.eventServiceFilterEventsCall(body, _callback);
    }
}
