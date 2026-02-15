package se.goencoder.loppiskassan.storage;

import se.goencoder.iloppis.model.V1Event;
import se.goencoder.iloppis.model.V1RevenueSplit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Persistent cache for iLoppis online events.
 * Events are cached as CachedOnlineEvent entries under ~/.loppiskassan/events/.
 * Used when the app starts offline to show previously discovered events.
 */
public class OnlineEventCache {

    // Cache TTL: 7 days chosen to balance offline usability with data freshness
    // - Long enough to support multi-day events without network
    // - Short enough to prevent severely outdated seller lists
    // - Aligns with typical flea market event durations
    public static final long CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000L; // 7 days
    private static final String CACHE_METADATA_FILENAME = "online_cache_metadata.json";

    /**
     * Cache an online event after successful register opening.
     * Stores: event metadata and revenue split. Credentials are optional.
     *
     * @param event       The V1Event from the API
     * @param apiKey      The exchanged API key (optional)
     * @param sellers     JSON string of approved sellers (optional)
     * @param split       Revenue split object
     */
    public static void cacheEvent(V1Event event, String apiKey, String sellers, V1RevenueSplit split) {
        if (event == null || event.getId() == null) {
            return;
        }

        try {
            LocalEventRepository.ensureBaseDirectories();
            Path eventDir = LocalEventPaths.getEventDir(event.getId());
            Files.createDirectories(eventDir);

            // Create cached event with current timestamp
            CachedOnlineEvent cached = new CachedOnlineEvent(
                    event.getId(),
                    event.getName(),
                    event.getDescription(),
                    event.getAddressStreet(),
                    event.getAddressCity(),
                    event.getMarketId(),
                    apiKey,
                    sellers,
                    split != null ? split.toJson() : "",
                    event.getStartTime(),
                    event.getEndTime(),
                    OffsetDateTime.now()
            );

            // Write to cache metadata file
            Path cachePath = eventDir.resolve(CACHE_METADATA_FILENAME);
            Files.writeString(cachePath, cached.toJsonString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        } catch (IOException e) {
            // Log warning but don't fail - app can continue without cache
            System.err.println("Warning: Failed to cache event " + event.getId() + ": " + e.getMessage());
        }
    }

    /**
     * Load all cached online events from disk.
     * Filters out entries older than CACHE_TTL_MS.
     *
     * @return List of cached events, may be empty
     */
    public static List<CachedOnlineEvent> loadCachedEvents() {
        List<CachedOnlineEvent> result = new ArrayList<>();

        try {
            Path eventsDir = LocalEventPaths.getEventsDir();
            if (Files.notExists(eventsDir)) {
                return result;
            }

            try (Stream<Path> paths = Files.list(eventsDir)) {
                paths.filter(Files::isDirectory)
                        .forEach(eventDir -> {
                            Path cachePath = eventDir.resolve(CACHE_METADATA_FILENAME);
                            if (Files.exists(cachePath)) {
                                try {
                                    String json = Files.readString(cachePath, StandardCharsets.UTF_8);
                                    CachedOnlineEvent cached = CachedOnlineEvent.fromJsonString(json);

                                    // Filter out expired entries
                                    if (!cached.isExpired(CACHE_TTL_MS)) {
                                        result.add(cached);
                                    }
                                } catch (IOException e) {
                                    // Ignore corrupted cache files
                                    System.err.println("Warning: Failed to load cached event from " + cachePath + ": " + e.getMessage());
                                }
                            }
                        });
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to list cached events: " + e.getMessage());
        }

        return result;
    }

    /**
     * Load a specific cached event by ID.
     *
     * @param eventId The event ID
     * @return The cached event, or null if not found or expired
     */
    public static CachedOnlineEvent loadCachedEvent(String eventId) {
        if (eventId == null) {
            return null;
        }

        try {
            Path cachePath = LocalEventPaths.getEventDir(eventId).resolve(CACHE_METADATA_FILENAME);
            if (Files.notExists(cachePath)) {
                return null;
            }

            String json = Files.readString(cachePath, StandardCharsets.UTF_8);
            return CachedOnlineEvent.fromJsonString(json);

        } catch (IOException e) {
            System.err.println("Warning: Failed to load cached event " + eventId + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Remove a cached event (e.g., after server confirms deletion).
     *
     * @param eventId The event ID to remove from cache
     */
    public static void removeCache(String eventId) {
        if (eventId == null) {
            return;
        }

        try {
            Path cachePath = LocalEventPaths.getEventDir(eventId).resolve(CACHE_METADATA_FILENAME);
            if (Files.exists(cachePath)) {
                Files.delete(cachePath);
            }
        } catch (IOException e) {
            System.err.println("Warning: Failed to remove cache for event " + eventId + ": " + e.getMessage());
        }
    }

    /**
     * Check if a valid (non-expired) cache exists for the given event.
     *
     * @param eventId The event ID
     * @return true if a valid cache exists
     */
    public static boolean hasCachedEvent(String eventId) {
        if (eventId == null) {
            return false;
        }

        CachedOnlineEvent cached = loadCachedEvent(eventId);
        return cached != null && !cached.isExpired(CACHE_TTL_MS);
    }

    /**
     * Check if cached credentials (API key) exist for the given event.
     *
     * @param eventId The event ID
     * @return true if a valid cache exists and includes an API key
     */
    public static boolean hasCachedCredentials(String eventId) {
        if (eventId == null) {
            return false;
        }

        CachedOnlineEvent cached = loadCachedEvent(eventId);
        return cached != null && !cached.isExpired(CACHE_TTL_MS) && cached.hasApiKey();
    }

    /**
     * Clear cached credentials for the given event while keeping event metadata.
     *
     * @param eventId The event ID
     */
    public static void clearCachedCredentials(String eventId) {
        if (eventId == null) {
            return;
        }

        CachedOnlineEvent cached = loadCachedEvent(eventId);
        if (cached == null) {
            return;
        }

        try {
            LocalEventRepository.ensureBaseDirectories();
            Path eventDir = LocalEventPaths.getEventDir(eventId);
            Files.createDirectories(eventDir);

            CachedOnlineEvent updated = new CachedOnlineEvent(
                    cached.getEventId(),
                    cached.getEventName(),
                    cached.getDescription(),
                    cached.getAddressStreet(),
                    cached.getAddressCity(),
                    cached.getMarketId(),
                    "",
                    "",
                    cached.getRevenueSplitJson(),
                    cached.getStartTime(),
                    cached.getEndTime(),
                    cached.getCachedAt()
            );

            Path cachePath = eventDir.resolve(CACHE_METADATA_FILENAME);
            Files.writeString(cachePath, updated.toJsonString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        } catch (IOException e) {
            System.err.println("Warning: Failed to clear cached credentials for event " + eventId + ": " + e.getMessage());
        }
    }

    /**
     * Refresh caches with fresh data from the server.
     * Updates existing cached events with new metadata (name, description, etc).
     *
     * @param events List of fresh events from the API
     */
    public static void refreshCaches(List<V1Event> events) {
        if (events == null) {
            return;
        }

        for (V1Event event : events) {
            if (event.getId() == null) {
                continue;
            }

            // Only refresh if cache already exists (don't auto-cache all events)
            Path cachePath = LocalEventPaths.getEventDir(event.getId()).resolve(CACHE_METADATA_FILENAME);
            if (Files.exists(cachePath)) {
                try {
                    // Load existing cache to preserve credentials
                    CachedOnlineEvent existing = loadCachedEvent(event.getId());
                    if (existing != null) {
                        // Create updated cache with fresh event data but existing credentials
                        CachedOnlineEvent updated = new CachedOnlineEvent(
                                event.getId(),
                                event.getName(),
                                event.getDescription(),
                                event.getAddressStreet(),
                                event.getAddressCity(),
                                event.getMarketId(),
                                existing.getApiKey(),
                                existing.getApprovedSellersJson(),
                                existing.getRevenueSplitJson(),
                                event.getStartTime(),
                                event.getEndTime(),
                                OffsetDateTime.now()  // Update cache timestamp
                        );

                        Files.writeString(cachePath, updated.toJsonString(), StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                    }
                } catch (IOException e) {
                    System.err.println("Warning: Failed to refresh cache for event " + event.getId() + ": " + e.getMessage());
                }
            }
        }
    }
}
