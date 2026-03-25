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
        private String cashierName;
        
        public GlobalConfig() {}
    }
    
    private static void load() {
        try {
            // Create config directory if it doesn't exist
            Files.createDirectories(AppPaths.getConfigDir());

            Path configPath = getConfigPath();
            if (Files.exists(configPath)) {
                try (Reader reader = new FileReader(configPath.toFile())) {
                    config = GSON.fromJson(reader, GlobalConfig.class);
                    if (config == null) {
                        config = new GlobalConfig();
                    }
                } catch (Exception ex) {
                    // Handle malformed JSON gracefully (GSON throws RuntimeException on parse errors)
                    config = new GlobalConfig();
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
            try (Writer writer = new FileWriter(getConfigPath().toFile())) {
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

    // Cashier display name / alias for this machine.
    public static String getCashierName() {
        if (config.cashierName == null) {
            return null;
        }
        String trimmed = config.cashierName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
    
    public static void setCashierName(String name) {
        String normalized = normalizeCashierName(name);
        if (java.util.Objects.equals(config.cashierName, normalized)) {
            return;
        }
        config.cashierName = normalized;
        save();
    }
    
    /**
     * Reset all global settings to defaults
     */
    public static void reset() {
        config = new GlobalConfig();
        save();
    }

    private static Path getConfigPath() {
        return AppPaths.getConfigDir().resolve(CONFIG_FILE);
    }

    private static String normalizeCashierName(String name) {
        if (name == null) {
            return null;
        }
        String trimmed = name.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
