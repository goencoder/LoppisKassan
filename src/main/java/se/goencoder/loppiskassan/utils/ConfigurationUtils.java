package se.goencoder.loppiskassan.utils;

import se.goencoder.loppiskassan.config.ConfigurationStore;


public class ConfigurationUtils {

    public static boolean isOfflineMode() {
        return ConfigurationStore.OFFLINE_EVENT_BOOL.getBooleanValueOrDefault(false);
    }
}

