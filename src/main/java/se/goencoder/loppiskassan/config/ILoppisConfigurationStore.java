package se.goencoder.loppiskassan.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import se.goencoder.loppiskassan.ui.Popup;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration store for iLoppis mode (online, API-based).
 * Contains settings specific to iLoppis event management including API credentials
 * and cached data for offline validation.
 * 
 * Stored in: config/iloppis-mode.json
 */
public class ILoppisConfigurationStore {
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = "iloppis-mode.json";
    private static final Path CONFIG_PATH = Paths.get(CONFIG_DIR, CONFIG_FILE);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static ILoppisConfig config;
    
    static {
        load();
    }
    
    /**
     * iLoppis mode configuration data class
     */
    private static class ILoppisConfig {
        private String eventId;           // UUID from API
        private String apiKey;            // Authentication key
        private String approvedSellers;   // JSON array of approved vendor IDs (cached for offline validation)
        private String revenueSplit;      // JSON string of revenue split configuration
        private String eventData;         // JSON string of event metadata
        
        public ILoppisConfig() {}
    }
    
    private static void load() {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
            
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                    config = GSON.fromJson(reader, ILoppisConfig.class);
                    if (config == null) {
                        config = new ILoppisConfig();
                    }
                }
            } else {
                config = new ILoppisConfig();
            }
        } catch (IOException ex) {
            Popup.FATAL.showAndWait(
                    "Configuration Error",
                    "Failed to load iLoppis configuration: " + ex.getMessage());
            config = new ILoppisConfig();
        }
    }
    
    private static void save() {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
            try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(config, writer);
            }
        } catch (IOException ex) {
            Popup.FATAL.showAndWait(
                    "Configuration Error",
                    "Failed to save iLoppis configuration: " + ex.getMessage());
        }
    }
    
    // Event ID
    public static String getEventId() {
        return config.eventId;
    }
    
    public static void setEventId(String eventId) {
        config.eventId = eventId;
        save();
    }
    
    // API Key
    public static String getApiKey() {
        return config.apiKey;
    }
    
    public static void setApiKey(String apiKey) {
        config.apiKey = apiKey;
        save();
    }
    
    // Approved Sellers (cached JSON array)
    public static String getApprovedSellers() {
        return config.approvedSellers;
    }
    
    public static void setApprovedSellers(String approvedSellers) {
        config.approvedSellers = approvedSellers;
        save();
    }
    
    // Revenue Split
    public static String getRevenueSplit() {
        return config.revenueSplit;
    }
    
    public static void setRevenueSplit(String revenueSplit) {
        config.revenueSplit = revenueSplit;
        save();
    }
    
    // Event Data
    public static String getEventData() {
        return config.eventData;
    }
    
    public static void setEventData(String eventData) {
        config.eventData = eventData;
        save();
    }
    
    /**
     * Check if iLoppis mode is configured
     */
    public static boolean isConfigured() {
        return config.eventId != null && !config.eventId.isEmpty() 
            && config.apiKey != null && !config.apiKey.isEmpty();
    }
    
    /**
     * Reset all iLoppis mode settings
     */
    public static void reset() {
        config = new ILoppisConfig();
        save();
    }
}
