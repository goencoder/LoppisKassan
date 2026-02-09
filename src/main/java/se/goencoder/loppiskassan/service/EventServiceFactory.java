package se.goencoder.loppiskassan.service;

import se.goencoder.loppiskassan.config.ConfigurationStore;

/**
 * Factory for creating the appropriate EventService implementation
 * based on the current mode (local vs online).
 */
public class EventServiceFactory {

    private static EventService localService;
    private static EventService onlineService;

    /**
     * Get the appropriate EventService based on current mode.
     * @return LocalEventService if in local mode, OnlineEventService otherwise
     */
    public static EventService getEventService() {
        boolean isLocal = ConfigurationStore.LOCAL_EVENT_BOOL.getBooleanValueOrDefault(false);
        
        if (isLocal) {
            if (localService == null) {
                localService = new LocalEventService();
            }
            return localService;
        } else {
            if (onlineService == null) {
                onlineService = new OnlineEventService();
            }
            return onlineService;
        }
    }

    /**
     * Check if currently in local mode.
     * @return true if local mode
     */
    public static boolean isLocalMode() {
        return ConfigurationStore.LOCAL_EVENT_BOOL.getBooleanValueOrDefault(false);
    }

    /**
     * Reset the service instances (useful for testing).
     */
    static void reset() {
        localService = null;
        onlineService = null;
    }
}
