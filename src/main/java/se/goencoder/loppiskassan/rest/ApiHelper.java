package se.goencoder.loppiskassan.rest;
import se.goencoder.iloppis.api.*;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.loppiskassan.config.ConfigurationStore;


/**
 * Hjälpklass för att hantera anrop till API:et.
 * Den har statista metoder för att hämta klienter och injicera autentisering.
 */
public enum ApiHelper {
    INSTANCE("127.0.0.1", 8080);
    private final FixedApiClient apiClient;
    private final SoldItemsServiceApi soldItemsServiceApi;
    private final ApiKeyServiceApi apiKeyServiceApi;
    private final EventServiceApi eventServiceApi;
    private final VendorServiceApi vendorServiceApi;
    private final ApprovedMarketServiceApi approvedMarketServiceApi;

    ApiHelper(String host, int port) {
        // Use our fixed API client implementation
        this.apiClient = new FixedApiClient();
        this.apiClient.setBasePath("http://" + host + ":" + port);
        this.apiClient.setUserAgent("LoppisKassan/2.0.0");
        
        // Configure the JSON serialization to use pretty printing
        this.apiClient.getJSON().setGson(this.apiClient.getJSON().getGson().newBuilder().setPrettyPrinting().create());

        if (ConfigurationStore.API_KEY_STR.get() != null) {
            setCurrentApiKey(ConfigurationStore.API_KEY_STR.get());
        }

        // Create API instances with our fixed client
        this.soldItemsServiceApi = new SoldItemsServiceApi(apiClient);
        this.apiKeyServiceApi = new ApiKeyServiceApi(apiClient);
        this.eventServiceApi = new EventServiceApi(apiClient);
        this.vendorServiceApi = new VendorServiceApi(apiClient);
        this.approvedMarketServiceApi = new ApprovedMarketServiceApi(apiClient);
    }

    public SoldItemsServiceApi getSoldItemsServiceApi() {
        return INSTANCE.soldItemsServiceApi;
    }

    public ApiKeyServiceApi getApiKeyServiceApi() {
        return INSTANCE.apiKeyServiceApi;
    }

    public EventServiceApi getEventServiceApi() {
        return INSTANCE.eventServiceApi;
    }

    public VendorServiceApi getVendorServiceApi() {
        return INSTANCE.vendorServiceApi;
    }

    public ApprovedMarketServiceApi getApprovedMarketServiceApi() {
        return INSTANCE.approvedMarketServiceApi;
    }

    public void setCurrentApiKey(String apiKey) {
        this.apiClient.addDefaultHeader("Authorization", "Bearer " + apiKey);
    }

    /**
     * Heuristic to detect a network or connectivity type error.
     * You can expand this logic depending on how your `ApiException` is structured.
     */
    public static boolean isLikelyNetworkError(Throwable e) {
        if (e instanceof ApiException apiEx) {
            // code=0 often indicates a connection failure or unknown host
            return apiEx.getCode() == 0;
        }
        // Could be UnknownHostException, SocketTimeoutException, etc.
        // For brevity, treat everything else as a potential network error.
        return true;
    }
}
