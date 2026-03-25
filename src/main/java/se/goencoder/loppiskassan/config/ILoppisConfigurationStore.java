package se.goencoder.loppiskassan.config;

import se.goencoder.loppiskassan.storage.OnlineEventCredentialsStore;

import java.nio.file.Path;

/**
 * Configuration store for iLoppis mode (online, API-based).
 * Contains settings specific to iLoppis event management including API credentials
 * and cached data for offline validation.
 * 
 * Stored in: ~/.loppiskassan/config/iloppis-mode.json
 */
public class ILoppisConfigurationStore extends ConfigurationStore<ILoppisConfigurationStore.ILoppisConfig> {
    private static final String STAGING_API_BASE_URL = "https://iloppis-staging.fly.dev";
    private static final String PRODUCTION_API_BASE_URL = "https://iloppis.se";
    private static final String CONFIG_FILE = "iloppis-mode.json";
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve(CONFIG_FILE);
    
    private static final ILoppisConfigurationStore INSTANCE = new ILoppisConfigurationStore();
    
    static {
        INSTANCE.load();
        INSTANCE.migrateLegacyStagingBaseUrl();
    }
    
    private ILoppisConfigurationStore() {}
    
    @Override
    protected Path getConfigPath() {
        return CONFIG_PATH;
    }
    
    @Override
    protected ILoppisConfig createDefaultConfig() {
        return new ILoppisConfig();
    }
    
    @Override
    protected Class<ILoppisConfig> getConfigClass() {
        return ILoppisConfig.class;
    }
    
    @Override
    protected String getModeName() {
        return "iLoppis";
    }
    
    /**
     * iLoppis mode configuration data class
     */
    static class ILoppisConfig {
        private String eventId;           // UUID from API
        private String apiBaseUrl;        // API base URL (e.g., http://127.0.0.1:8080)
        private String approvedSellers;   // JSON array of approved vendor IDs (cached for offline validation)
        private String revenueSplit;      // JSON string of revenue split configuration
        private String eventData;         // JSON string of event metadata
        
        public ILoppisConfig() {}
    }
    
    // Event ID
    public static String getEventId() {
        return INSTANCE.config.eventId;
    }
    
    public static void setEventId(String eventId) {
        INSTANCE.config.eventId = eventId;
        INSTANCE.save();
    }
    
    // API Key (per-event, stored in OnlineEventCredentialsStore)
    public static String getApiKey() {
        return OnlineEventCredentialsStore.getApiKey(getEventId());
    }

    public static void setApiKey(String apiKey) {
        OnlineEventCredentialsStore.setApiKey(INSTANCE.config.eventId, apiKey);
        INSTANCE.save();
    }
    
    // API Base URL
    public static String getApiBaseUrl() {
        // Check environment variable first (useful for testing with toxiproxy)
        String envUrl = System.getenv("ILOPPIS_API_URL");
        if (envUrl != null && !envUrl.isBlank()) {
            return envUrl;
        }
        // Fall back to configured value or default
        return INSTANCE.config.apiBaseUrl != null && !INSTANCE.config.apiBaseUrl.isBlank() 
            ? INSTANCE.config.apiBaseUrl 
            : PRODUCTION_API_BASE_URL;
    }
    
    public static void setApiBaseUrl(String apiBaseUrl) {
        INSTANCE.config.apiBaseUrl = apiBaseUrl;
        INSTANCE.save();
    }
    
    // Approved Sellers (cached JSON array)
    public static String getApprovedSellers() {
        return INSTANCE.config.approvedSellers;
    }
    
    public static void setApprovedSellers(String approvedSellers) {
        INSTANCE.config.approvedSellers = approvedSellers;
        INSTANCE.save();
    }
    
    // Revenue Split
    public static String getRevenueSplit() {
        return INSTANCE.config.revenueSplit;
    }
    
    public static void setRevenueSplit(String revenueSplit) {
        INSTANCE.config.revenueSplit = revenueSplit;
        INSTANCE.save();
    }
    
    // Event Data
    public static String getEventData() {
        return INSTANCE.config.eventData;
    }
    
    public static void setEventData(String eventData) {
        INSTANCE.config.eventData = eventData;
        INSTANCE.save();
    }
    
    /**
     * Check if iLoppis mode is configured
     */
    public static boolean isConfigured() {
        String apiKey = getApiKey();
        return INSTANCE.config.eventId != null && !INSTANCE.config.eventId.isEmpty()
            && apiKey != null && !apiKey.isEmpty();
    }
    
    /**
     * Reset all iLoppis mode settings
     */
    public static void reset() {
        INSTANCE.config = new ILoppisConfig();
        INSTANCE.save();
    }

    private void migrateLegacyStagingBaseUrl() {
        String configured = config.apiBaseUrl;
        if (configured == null || configured.isBlank()) {
            return;
        }
        if (!normalizeUrl(configured).equals(normalizeUrl(STAGING_API_BASE_URL))) {
            return;
        }
        config.apiBaseUrl = PRODUCTION_API_BASE_URL;
        save();
    }

    private static String normalizeUrl(String url) {
        if (url == null) {
            return "";
        }
        String normalized = url.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

}
