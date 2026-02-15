package se.goencoder.loppiskassan.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class LocalEventRepository {

    public static void ensureBaseDirectories() throws IOException {
        Files.createDirectories(LocalEventPaths.getEventsDir());
    }

    public static void ensureEventStorage(String eventId) throws IOException {
        ensureBaseDirectories();
        Path eventDir = LocalEventPaths.getEventDir(eventId);
        Files.createDirectories(eventDir);
        Path pendingPath = LocalEventPaths.getPendingItemsPath(eventId);
        if (Files.notExists(pendingPath)) {
            Files.createFile(pendingPath);
        }
        Path soldPath = LocalEventPaths.getSoldItemsPath(eventId);
        if (Files.notExists(soldPath)) {
            Files.createFile(soldPath);
        }
    }

    public static LocalEvent create(LocalEvent event) throws IOException {
        ensureBaseDirectories();
        Path eventDir = LocalEventPaths.getEventDir(event.getEventId());
        if (Files.exists(eventDir)) {
            throw new IOException("Event directory already exists: " + eventDir);
        }
        Files.createDirectories(eventDir);

        Path metadataPath = LocalEventPaths.getLocalMetadataPath(event.getEventId());
        Files.writeString(metadataPath, event.toJsonString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

        Path pendingPath = LocalEventPaths.getPendingItemsPath(event.getEventId());
        Files.createFile(pendingPath);

        Path soldPath = LocalEventPaths.getSoldItemsPath(event.getEventId());
        Files.createFile(soldPath);

        return event;
    }

    public static LocalEvent load(String eventId) throws IOException {
        Path metadataPath = resolveLocalMetadataPath(eventId);
        if (Files.notExists(metadataPath)) {
            return null;
        }
        String json = Files.readString(metadataPath, StandardCharsets.UTF_8);
        return LocalEvent.fromJsonString(json);
    }

    public static void save(LocalEvent event) throws IOException {
        ensureEventStorage(event.getEventId());
        Path metadataPath = resolveLocalMetadataPath(event.getEventId());
        Files.writeString(metadataPath, event.toJsonString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    public static List<LocalEvent> loadAll() throws IOException {
        ensureBaseDirectories();
        List<LocalEvent> events = new ArrayList<>();
        try (Stream<Path> paths = Files.list(LocalEventPaths.getEventsDir())) {
            paths.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(Path::getFileName))
                    .forEach(dir -> {
                        String eventId = dir.getFileName().toString();
                        Path metadataPath = resolveLocalMetadataPath(eventId);
                        if (Files.exists(metadataPath)) {
                            try {
                                String json = Files.readString(metadataPath, StandardCharsets.UTF_8);
                                events.add(LocalEvent.fromJsonString(json));
                            } catch (IOException ignored) {
                                // Skip invalid metadata entries
                            }
                        }
                    });
        }
        return events;
    }

    /**
     * Delete a local event and all its data files.
     * @param eventId The event ID to delete
     * @throws IOException if deletion fails
     */
    public static void delete(String eventId) throws IOException {
        Path eventDir = LocalEventPaths.getEventDir(eventId);
        if (!Files.exists(eventDir)) {
            return; // Already deleted
        }
        // Delete all files and subdirectories recursively
        deleteRecursively(eventDir);
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            try (Stream<Path> children = Files.list(path)) {
                for (Path child : children.toList()) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

    private static Path resolveLocalMetadataPath(String eventId) {
        Path newPath = LocalEventPaths.getLocalMetadataPath(eventId);
        if (Files.exists(newPath)) {
            return newPath;
        }

        Path legacyPath = LocalEventPaths.getEventDir(eventId)
                .resolve(LocalEventPaths.LEGACY_METADATA_FILE_NAME);
        if (Files.exists(legacyPath)) {
            try {
                Files.move(legacyPath, newPath, StandardCopyOption.REPLACE_EXISTING);
                return newPath;
            } catch (IOException e) {
                return legacyPath;
            }
        }

        return newPath;
    }
}
