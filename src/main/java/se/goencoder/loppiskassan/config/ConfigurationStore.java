package se.goencoder.loppiskassan.config;
import se.goencoder.loppiskassan.ui.Popup;

import java.io.*;
import java.util.Properties;


public enum ConfigurationStore {
    EVENT_ID_STR("event_id"),
    API_KEY_STR("api_key"),
    APPROVED_SELLERS_JSON("approved_sellers");

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

    public String get() {
        return properties.getProperty(key);
    }

    public void set(String value) {
        properties.setProperty(key, value);
        saveProperties();
    }

    private void saveProperties() {
        try (OutputStream output = new FileOutputStream(CONFIG_FILE_PATH)) {
            properties.store(output, "Application Configuration");
        } catch (IOException ex) {
            Popup.FATAL.showAndWait("Configuration Error", "Failed to save the configuration file: " + CONFIG_FILE_PATH);
        }
    }


}


