package se.goencoder.loppiskassan.config;

/**
 * Singleton that holds the operating mode for the current session.
 * Set once at startup from the splash screen; immutable afterwards.
 */
public final class AppModeManager {

    private static AppMode currentMode;

    private AppModeManager() {}

    /** Set the mode (call once at startup). */
    public static void setMode(AppMode mode) {
        currentMode = mode;
    }

    /** Get the current mode. */
    public static AppMode getMode() {
        return currentMode;
    }

    /** Convenience: true when running in local-only mode. */
    public static boolean isLocalMode() {
        return currentMode == AppMode.LOCAL;
    }

    /** Convenience: true when running in iLoppis (online) mode. */
    public static boolean isILoppisMode() {
        return currentMode == AppMode.ILOPPIS;
    }
}
