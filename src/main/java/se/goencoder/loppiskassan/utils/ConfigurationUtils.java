package se.goencoder.loppiskassan.utils;

import se.goencoder.loppiskassan.config.AppModeManager;


public class ConfigurationUtils {

    public static boolean isLocalMode() {
        return AppModeManager.isLocalMode();
    }
}
