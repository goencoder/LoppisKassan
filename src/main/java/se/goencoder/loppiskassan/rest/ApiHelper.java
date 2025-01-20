package se.goencoder.loppiskassan.rest;
import se.goencoder.iloppis.api.ApiKeyServiceApi;
import se.goencoder.iloppis.api.EventServiceApi;
import se.goencoder.iloppis.api.SoldItemsServiceApi; // ERROR: Cannot resolve symbol 'iloppis'
import se.goencoder.iloppis.api.VendorApplicationServiceApi;
import se.goencoder.iloppis.invoker.ApiClient;
import se.goencoder.loppiskassan.config.ConfigurationStore;


/**
 * Hjälpklass för att hantera anrop till API:et.
 * Den har statista metoder för att hämta klienter och injicera autentisering.
 */
public enum ApiHelper {
    INSTANCE("127.0.0.1", 8080);
    private String eventId;
    private final ApiClient apiClient;
    private final SoldItemsServiceApi soldItemsServiceApi;
    private final ApiKeyServiceApi apiKeyServiceApi;
    private final EventServiceApi eventServiceApi;
    private final VendorApplicationServiceApi vendorApplicationServiceApi;

    ApiHelper(String host, int port) {
        this.apiClient = new ApiClient();
        this.apiClient.setBasePath("http://" + host + ":" + port);
        if (ConfigurationStore.API_KEY_STR.get() != null) {
            setCurrentApiKey(ConfigurationStore.API_KEY_STR.get());
        }
        this.soldItemsServiceApi = new SoldItemsServiceApi(apiClient);
        this.apiKeyServiceApi = new ApiKeyServiceApi(apiClient);
        this.eventServiceApi = new EventServiceApi(apiClient);
        this.vendorApplicationServiceApi = new VendorApplicationServiceApi(apiClient);


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
    public VendorApplicationServiceApi getVendorApplicationServiceApi() {
        return INSTANCE.vendorApplicationServiceApi;
    }

    public void setCurrentApiKey(String apiKey) {
        this.apiClient.addDefaultHeader("Authorization", "Bearer " + apiKey);
    }

}
