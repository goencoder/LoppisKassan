package se.goencoder.loppiskassan.config;

import java.nio.file.Path;

/**
 * Configuration store for Local mode (offline, file-based).
 * Contains settings specific to local event management.
 * 
 * Stored in: ~/.loppiskassan/config/local-mode.json
 */
public class LocalConfigurationStore extends ConfigurationStore<LocalConfigurationStore.LocalConfig> {
    
    private static final String CONFIG_FILE = "local-mode.json";
    private static final Path CONFIG_PATH = CONFIG_DIR.resolve(CONFIG_FILE);
    
    private static final LocalConfigurationStore INSTANCE = new LocalConfigurationStore();
    
    static {
        INSTANCE.load();
    }
    
    private LocalConfigurationStore() {}
    
    @Override
    protected Path getConfigPath() {
        return CONFIG_PATH;
    }
    
    @Override
    protected LocalConfig createDefaultConfig() {
        return new LocalConfig();
    }
    
    @Override
    protected Class<LocalConfig> getConfigClass() {
        return LocalConfig.class;
    }
    
    @Override
    protected String getModeName() {
        return "local";
    }
    
    /**
     * Local mode configuration data class
     */
    static class LocalConfig {
        private String eventId;
        private String eventData;  // JSON string of event metadata
        private String revenueSplit;  // JSON string of revenue split configuration
        
        public LocalConfig() {}
    }
    
    // Event ID
    public static String getEventId() {
        return INSTANCE.config.eventId;
    }
    
    public static void setEventId(String eventId) {
        INSTANCE.config.eventId = eventId;
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
    
    // Revenue Split
    public static String getRevenueSplit() {
        return INSTANCE.config.revenueSplit;
    }
    
    public static void setRevenueSplit(String revenueSplit) {
        INSTANCE.config.revenueSplit = revenueSplit;
        INSTANCE.save();
    }
    
    /**
     * Check if local mode is configured
     */
    public static boolean isConfigured() {
        return INSTANCE.config.eventId != null && !INSTANCE.config.eventId.isEmpty();
    }
    
    /**
     * Reset all local mode settings
     */
    public static void reset() {
        INSTANCE.config = new LocalConfig();
        INSTANCE.save();
    }
}
