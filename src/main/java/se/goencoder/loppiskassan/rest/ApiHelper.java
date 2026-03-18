package se.goencoder.loppiskassan.rest;
import se.goencoder.iloppis.api.*;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.loppiskassan.config.ILoppisConfigurationStore;


/**
 * Singleton shared API client. All production code MUST use this for API calls.
 *
 * Authentication is handled implicitly: {@link AuthInterceptor} injects the
 * Authorization header on every OkHttp request by reading {@link #getCurrentApiKey()}.
 * Call {@link #setCurrentApiKey(String)} / {@link #clearCurrentApiKey()} to update
 * credentials — all in-flight and future requests pick up the change immediately.
 */
public enum ApiHelper {
    INSTANCE;
    private final FixedApiClient apiClient;
    private final SoldItemsServiceApi soldItemsServiceApi;
    private final ApiKeyServiceApi apiKeyServiceApi;
    private final EventServiceApi eventServiceApi;
    private final VendorServiceApi vendorServiceApi;
    private final ApprovedMarketServiceApi approvedMarketServiceApi;
    private final StatsServiceApi statsServiceApi;

    /** Current API key — read by {@link AuthInterceptor} at request time. */
    private volatile String currentApiKey;

    ApiHelper() {
        this.apiClient = new FixedApiClient();
        String baseUrl = ILoppisConfigurationStore.getApiBaseUrl();
        this.apiClient.setBasePath(baseUrl);
        this.apiClient.setUserAgent("LoppisKassan/2.0.0");

        this.apiClient.setConnectTimeout(5000);
        this.apiClient.setReadTimeout(5000);
        this.apiClient.setWriteTimeout(5000);

        this.apiClient.getJSON().setGson(
                this.apiClient.getJSON().getGson().newBuilder().setPrettyPrinting().create());

        // Seed from persisted key (may be null if not remembered)
        this.currentApiKey = ILoppisConfigurationStore.getApiKey();

        this.soldItemsServiceApi = new SoldItemsServiceApi(apiClient);
        this.apiKeyServiceApi = new ApiKeyServiceApi(apiClient);
        this.eventServiceApi = new EventServiceApi(apiClient);
        this.vendorServiceApi = new VendorServiceApi(apiClient);
        this.approvedMarketServiceApi = new ApprovedMarketServiceApi(apiClient);
        this.statsServiceApi = new StatsServiceApi(apiClient);
    }

    // ── Service API accessors ──

    public SoldItemsServiceApi getSoldItemsServiceApi() {
        return soldItemsServiceApi;
    }

    public ApiKeyServiceApi getApiKeyServiceApi() {
        return apiKeyServiceApi;
    }

    public EventServiceApi getEventServiceApi() {
        return eventServiceApi;
    }

    public VendorServiceApi getVendorServiceApi() {
        return vendorServiceApi;
    }

    public ApprovedMarketServiceApi getApprovedMarketServiceApi() {
        return approvedMarketServiceApi;
    }

    public StatsServiceApi getStatsServiceApi() {
        return statsServiceApi;
    }

    // ── API key management ──

    public void setCurrentApiKey(String apiKey) {
        this.currentApiKey = apiKey;
    }

    public void clearCurrentApiKey() {
        this.currentApiKey = null;
    }

    public String getCurrentApiKey() {
        return this.currentApiKey;
    }

    /**
     * Returns the shared OkHttp client (with auth interceptor).
     * Use for raw HTTP calls (e.g., heartbeats) that don't go through generated APIs.
     */
    public okhttp3.OkHttpClient getHttpClient() {
        return this.apiClient.getHttpClient();
    }

    /** Returns the shared base URL. */
    public String getBasePath() {
        return this.apiClient.getBasePath();
    }

    // ── Utilities ──

    public static boolean isLikelyNetworkError(Throwable e) {
        if (e instanceof ApiException apiEx) {
            return apiEx.getCode() == 0;
        }
        return true;
    }
}
