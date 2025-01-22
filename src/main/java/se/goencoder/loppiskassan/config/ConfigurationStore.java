package se.goencoder.loppiskassan.config;
import se.goencoder.loppiskassan.ui.Popup;

import java.io.*;
import java.util.Properties;

/**
 * A simple configuration store for the application.
 * Naming conventions and how to use:
 * - Use the key as a string constant in the enum.
 * - Use the get() method to retrieve the value.
 * - Use the set() method to set the value.
 * - Use the getIntValueOrDefault() method to retrieve an integer value with a default value (Enum name has _INT).
 * - Use the setIntValue() method to set an integer value (Enum name has _INT).
 * - Use the getBooleanValueOrDefault() method to retrieve a boolean value with a default value (Enum name has _BOOL).
 * - Use the setBooleanValue() method to set a boolean value (Enum name has _BOOL).
 */
public enum ConfigurationStore {
    EVENT_JSON("event"),
    EVENT_ID_STR("event_id"),
    API_KEY_STR("api_key"),
    APPROVED_SELLERS_JSON("approved_sellers"),
    OFFLINE_EVENT_BOOL("offline_event"),
    REVENUE_SPLIT_JSON("revenue_split");

    private final String key;
    private static final Properties properties = new Properties();
    private static final String CONFIG_FILE_PATH = "config.properties";

    static {
        try {
            File configFile = new File(CONFIG_FILE_PATH);
            if (configFile.exists()) {
                try (InputStream input = new FileInputStream(configFile)) {
                    properties.load(input);
                }
            } else {
                // Create the file if it doesn't exist
                if (configFile.createNewFile()) {
                    try (OutputStream output = new FileOutputStream(configFile)) {
                        properties.store(output, "Application Configuration");
                    }
                }
            }
        } catch (IOException ex) {
            Popup.FATAL.showAndWait("Configuration Error", "Failed to open or create the configuration file: " + CONFIG_FILE_PATH);
        }
    }

    ConfigurationStore(String key) {
        this.key = key;
    }
    public static void reset() {
        properties.clear();
        saveProperties();
    }

    public String get() {
        return properties.getProperty(key);
    }

    public void set(String value) {
        properties.setProperty(key, value);
        saveProperties();
    }

    public boolean getBooleanValueOrDefault(boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }

    public void setBooleanValue(boolean value) {
        properties.setProperty(key, Boolean.toString(value));
        saveProperties();
    }

    private static void saveProperties() {
        try (OutputStream output = new FileOutputStream(CONFIG_FILE_PATH)) {
            properties.store(output, "Application Configuration");
        } catch (IOException ex) {
            Popup.FATAL.showAndWait("Configuration Error", "Failed to save the configuration file: " + CONFIG_FILE_PATH);
        }
    }


}


