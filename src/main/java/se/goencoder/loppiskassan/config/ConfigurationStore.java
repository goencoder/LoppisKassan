package se.goencoder.loppiskassan.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.util.AppPaths;
import se.goencoder.loppiskassan.ui.Popup;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Abstract base class for configuration stores using Template Method pattern.
 * Eliminates duplication between LocalConfigurationStore and ILoppisConfigurationStore.
 * 
 * Subclasses provide:
 * - Config file path
 * - Default config instance
 * - Config class type
 * - Mode name for error messages
 * 
 * @param <T> The configuration data class type
 */
public abstract class ConfigurationStore<T> {
    
    protected static final Path CONFIG_DIR = AppPaths.getConfigDir();
    protected static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    
    protected T config;
    
    /**
     * Get the path to the configuration file.
     */
    protected abstract Path getConfigPath();
    
    /**
     * Create a new default configuration instance.
     */
    protected abstract T createDefaultConfig();
    
    /**
     * Get the configuration class type for Gson deserialization.
     */
    protected abstract Class<T> getConfigClass();
    
    /**
     * Get the mode name for error messages ("local" or "iLoppis").
     */
    protected abstract String getModeName();
    
    /**
     * Template method: Load configuration from disk.
     * Common logic with mode-specific variations delegated to abstract methods.
     */
    protected final void load() {
        try {
            Files.createDirectories(CONFIG_DIR);
            
            if (Files.exists(getConfigPath())) {
                try (Reader reader = new FileReader(getConfigPath().toFile())) {
                    T loaded = GSON.fromJson(reader, getConfigClass());
                    config = (loaded != null) ? loaded : createDefaultConfig();
                }
            } else {
                config = createDefaultConfig();
            }
        } catch (Exception ex) {
            Popup.FATAL.showAndWait(
                LocalizationManager.tr("error.config_load.title"),
                LocalizationManager.tr("error.config_load.message", getModeName(), ex.getMessage()));
            config = createDefaultConfig();
        }
    }
    
    /**
     * Template method: Save configuration to disk.
     * Common logic with mode-specific variations delegated to abstract methods.
     */
    protected final void save() {
        try {
            Files.createDirectories(CONFIG_DIR);
            try (Writer writer = new FileWriter(getConfigPath().toFile())) {
                GSON.toJson(config, writer);
            }
        } catch (Exception ex) {
            Popup.FATAL.showAndWait(
                LocalizationManager.tr("error.config_save.title"),
                LocalizationManager.tr("error.config_save.message", getModeName(), ex.getMessage()));
        }
    }
}
