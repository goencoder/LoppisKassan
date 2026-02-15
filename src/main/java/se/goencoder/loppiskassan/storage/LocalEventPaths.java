package se.goencoder.loppiskassan.storage;

import se.goencoder.loppiskassan.util.AppPaths;

import java.nio.file.Path;

public final class LocalEventPaths {
    private static final String EVENTS_DIR_NAME = "events";
    private static final String ARCHIVE_DIR_NAME = "archive";
    private static final String LOCAL_METADATA_FILE_NAME = "local_metadata.json";
    private static final String ILOPPIS_METADATA_FILE_NAME = "iloppis_metadata.json";
    private static final String PENDING_ITEMS_FILE_NAME = "pending_items.jsonl";
    private static final String SOLD_ITEMS_FILE_NAME = "sold_items.jsonl";
    private static final String REJECTED_PURCHASES_FILE_NAME = "rejected_purchases.jsonl";

    public static final String LEGACY_METADATA_FILE_NAME = "metadata.json";

    private LocalEventPaths() {}

    public static Path getBaseDir() {
        return AppPaths.getBaseDir();
    }

    public static Path getEventsDir() {
        return getBaseDir().resolve(EVENTS_DIR_NAME);
    }

    public static Path getEventDir(String eventId) {
        return getEventsDir().resolve(eventId);
    }

    public static Path getLocalMetadataPath(String eventId) {
        return getEventDir(eventId).resolve(LOCAL_METADATA_FILE_NAME);
    }

    public static Path getIloppisMetadataPath(String eventId) {
        return getEventDir(eventId).resolve(ILOPPIS_METADATA_FILE_NAME);
    }

    public static Path getMetadataPath(String eventId) {
        return getLocalMetadataPath(eventId);
    }

    public static Path getPendingItemsPath(String eventId) {
        return getEventDir(eventId).resolve(PENDING_ITEMS_FILE_NAME);
    }

    public static Path getSoldItemsPath(String eventId) {
        return getEventDir(eventId).resolve(SOLD_ITEMS_FILE_NAME);
    }

    public static Path getRejectedPurchasesPath(String eventId) {
        return getEventDir(eventId).resolve(REJECTED_PURCHASES_FILE_NAME);
    }

    public static Path getArchiveDir(String eventId) {
        return getEventDir(eventId).resolve(ARCHIVE_DIR_NAME);
    }
}
