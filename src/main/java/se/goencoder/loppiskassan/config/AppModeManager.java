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
    
    /**
     * Get the event ID from the appropriate configuration store based on current mode.
     * @return Event ID or null if not configured
     */
    public static String getEventId() {
        if (isLocalMode()) {
            return LocalConfigurationStore.getEventId();
        } else {
            return ILoppisConfigurationStore.getEventId();
        }
    }
    
    /**
     * Set the event ID in the appropriate configuration store based on current mode.
     * @param eventId The event ID to store
     */
    public static void setEventId(String eventId) {
        if (isLocalMode()) {
            LocalConfigurationStore.setEventId(eventId);
        } else {
            ILoppisConfigurationStore.setEventId(eventId);
        }
    }
}
