package se.goencoder.loppiskassan.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration store for iLoppis mode (online, API-based).
 * Contains settings specific to iLoppis event management including API credentials
 * and cached data for offline validation.
 * 
 * Stored in: config/iloppis-mode.json
 */
public class ILoppisConfigurationStore extends ConfigurationStore<ILoppisConfigurationStore.ILoppisConfig> {
    
    private static final String CONFIG_FILE = "iloppis-mode.json";
    private static final Path CONFIG_PATH = Paths.get(CONFIG_DIR, CONFIG_FILE);
    
    private static final ILoppisConfigurationStore INSTANCE = new ILoppisConfigurationStore();
    
    static {
        INSTANCE.load();
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
        private String apiKey;            // Authentication key
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
    
    // API Key
    public static String getApiKey() {
        return INSTANCE.config.apiKey;
    }
    
    public static void setApiKey(String apiKey) {
        INSTANCE.config.apiKey = apiKey;
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
            : "http://127.0.0.1:8080";
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
        return INSTANCE.config.eventId != null && !INSTANCE.config.eventId.isEmpty() 
            && INSTANCE.config.apiKey != null && !INSTANCE.config.apiKey.isEmpty();
    }
    
    /**
     * Reset all iLoppis mode settings
     */
    public static void reset() {
        INSTANCE.config = new ILoppisConfig();
        INSTANCE.save();
    }
}
