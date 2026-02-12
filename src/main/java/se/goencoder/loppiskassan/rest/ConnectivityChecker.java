package se.goencoder.loppiskassan.rest;

import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.V1FilterEventsRequest;
import se.goencoder.iloppis.model.V1Pagination;
import se.goencoder.loppiskassan.config.ILoppisConfigurationStore;

/**
 * Proactive connectivity check for iLoppis backend.
 * Uses a lightweight API request with short timeout.
 */
public class ConnectivityChecker {

    private static final int CHECK_TIMEOUT_MS = 2000; // 2 seconds
    private static volatile boolean lastKnownOnline = false;

    /**
     * Check if the backend is reachable.
     * Makes a lightweight HTTP request with 2s timeout.
     * Updates lastKnownOnline state.
     *
     * @return true if server responds within timeout
     */
    public static boolean isOnline() {
        try {
            // Create a temporary API client with short timeout
            FixedApiClient testClient = new FixedApiClient();
            String baseUrl = ILoppisConfigurationStore.getApiBaseUrl();
            testClient.setBasePath(baseUrl);
            testClient.setConnectTimeout(CHECK_TIMEOUT_MS);
            testClient.setReadTimeout(CHECK_TIMEOUT_MS);
            testClient.setWriteTimeout(CHECK_TIMEOUT_MS);

            // Try a simple API call - list events with minimal page size
            se.goencoder.iloppis.api.EventServiceApi eventApi = new se.goencoder.iloppis.api.EventServiceApi(testClient);
            
            // Create a minimal request
            V1FilterEventsRequest request = new V1FilterEventsRequest();
            V1Pagination pagination = new V1Pagination();
            pagination.setPageSize(1);
            request.setPagination(pagination);
            
            eventApi.eventServiceFilterEvents(request);

            lastKnownOnline = true;
            return true;

        } catch (ApiException e) {
            // Any API error (timeout, connection refused, etc) means we're offline
            lastKnownOnline = false;
            return false;
        } catch (Exception e) {
            // Catch any other unexpected errors
            lastKnownOnline = false;
            return false;
        }
    }

    /**
     * Get the last known connectivity state without making a new request.
     *
     * @return true if last check was online
     */
    public static boolean isLastKnownOnline() {
        return lastKnownOnline;
    }
}
