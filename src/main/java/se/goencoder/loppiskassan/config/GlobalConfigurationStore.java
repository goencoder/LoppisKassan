package se.goencoder.loppiskassan.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.util.AppPaths;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Global configuration store for application-wide settings.
 * These settings persist across mode switches (Local vs iLoppis).
 * 
 * Stored in: ~/.loppiskassan/config/global.json
 */
public class GlobalConfigurationStore {
    private static final String CONFIG_FILE = "global.json";
    private static final Path CONFIG_PATH = AppPaths.getConfigDir().resolve(CONFIG_FILE);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    private static GlobalConfig config;
    
    static {
        load();
    }
    
    /**
     * Global configuration data class
     */
    private static class GlobalConfig {
        private String language = "sv";
        
        public GlobalConfig() {}
    }
    
    private static void load() {
        try {
            // Create config directory if it doesn't exist
            Files.createDirectories(AppPaths.getConfigDir());
            
            if (Files.exists(CONFIG_PATH)) {
                try (Reader reader = new FileReader(CONFIG_PATH.toFile())) {
                    config = GSON.fromJson(reader, GlobalConfig.class);
                    if (config == null) {
                        config = new GlobalConfig();
                    }
                }
            } else {
                config = new GlobalConfig();
                save();
            }
        } catch (IOException ex) {
            Popup.FATAL.showAndWait(
                    "Configuration Error",
                    "Failed to load global configuration: " + ex.getMessage());
            config = new GlobalConfig();
        }
    }
    
    private static void save() {
        try {
            Files.createDirectories(AppPaths.getConfigDir());
            try (Writer writer = new FileWriter(CONFIG_PATH.toFile())) {
                GSON.toJson(config, writer);
            }
        } catch (IOException ex) {
            Popup.FATAL.showAndWait(
                    "Configuration Error",
                    "Failed to save global configuration: " + ex.getMessage());
        }
    }
    
    // Language
    public static String getLanguage() {
        return config.language != null ? config.language : "sv";
    }
    
    public static void setLanguage(String language) {
        config.language = language;
        save();
    }
    
    /**
     * Reset all global settings to defaults
     */
    public static void reset() {
        config = new GlobalConfig();
        save();
    }
}
