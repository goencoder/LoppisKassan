package se.goencoder.loppiskassan.storage;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Stores event-specific iLoppis credentials separately from event metadata.
 */
public final class OnlineEventCredentialsStore {

    private OnlineEventCredentialsStore() {}

    public static String getApiKey(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return null;
        }
        return readApiKey(LocalEventPaths.getIloppisCredentialsPath(eventId));
    }

    public static void setApiKey(String eventId, String apiKey) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        writeApiKey(LocalEventPaths.getIloppisCredentialsPath(eventId), apiKey);
    }

    static String readApiKey(Path credentialsPath) {
        if (credentialsPath == null || Files.notExists(credentialsPath) || !Files.isRegularFile(credentialsPath)) {
            return null;
        }

        try {
            JSONObject json = new JSONObject(Files.readString(credentialsPath, StandardCharsets.UTF_8));
            String apiKey = json.optString("apiKey", "").trim();
            return apiKey.isEmpty() ? null : apiKey;
        } catch (Exception e) {
            return null;
        }
    }

    static void writeApiKey(Path credentialsPath, String apiKey) {
        if (credentialsPath == null) {
            return;
        }

        try {
            if (apiKey == null || apiKey.isBlank()) {
                Files.deleteIfExists(credentialsPath);
                return;
            }

            Files.createDirectories(credentialsPath.getParent());
            JSONObject json = new JSONObject();
            json.put("apiKey", apiKey.trim());
            Files.writeString(
                    credentialsPath,
                    json.toString(2),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            System.err.println("Warning: Failed to persist event credentials " + credentialsPath + ": " + e.getMessage());
        }
    }
}
