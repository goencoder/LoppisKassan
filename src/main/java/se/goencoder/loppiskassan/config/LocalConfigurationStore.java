package se.goencoder.loppiskassan.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import se.goencoder.loppiskassan.ui.Popup;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Configuration store for Local mode (offline, file-based).
 * Contains settings specific to local event management.
 * 
 * Stored in: config/local-mode.json
 */
public class LocalConfigurationStore {
    private static final String CONFIG_DIR = "config";
    private static final String CONFIG_FILE = "local-mode.json";
    private static final Path CONFIG_PATH = Paths.get(CONFIG_DIR, CONFIG_FILE);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static LocalConfig config;
    
    static {
        load();
    }
    
    /**
     * Local mode configuration data class
     */
    private static class LocalConfig {
        private String eventId;
        private String eventData;  // JSON string of event metadata
        private String revenueSplit;  // JSON string of revenue split configuration
        
        public LocalConfig() {}
    }
    
    private static void load() {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
            
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                    config = GSON.fromJson(reader, LocalConfig.class);
                    if (config == null) {
                        config = new LocalConfig();
                    }
                }
            } else {
                config = new LocalConfig();
            }
        } catch (IOException ex) {
            Popup.FATAL.showAndWait(
                    "Configuration Error",
                    "Failed to load local configuration: " + ex.getMessage());
            config = new LocalConfig();
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
                    "Failed to save local configuration: " + ex.getMessage());
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
    
    // Event Data
    public static String getEventData() {
        return config.eventData;
    }
    
    public static void setEventData(String eventData) {
        config.eventData = eventData;
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
    
    /**
     * Check if local mode is configured
     */
    public static boolean isConfigured() {
        return config.eventId != null && !config.eventId.isEmpty();
    }
    
    /**
     * Reset all local mode settings
     */
    public static void reset() {
        config = new LocalConfig();
        save();
    }
}
