package se.goencoder.loppiskassan.utils;

import se.goencoder.loppiskassan.config.ConfigurationStore;


public class ConfigurationUtils {

    public static boolean isLocalMode() {
        return ConfigurationStore.LOCAL_EVENT_BOOL.getBooleanValueOrDefault(false);
    }
}
