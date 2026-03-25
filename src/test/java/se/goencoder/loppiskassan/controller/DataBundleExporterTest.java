package se.goencoder.loppiskassan.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataBundleExporterTest {

    @TempDir
    Path tempDir;

    @Test
    void sanitizeRemovesSpecialCharacters() {
        assertEquals("kassa1", DataBundleExporter.sanitize("Kassa 1"));
        assertEquals("entren", DataBundleExporter.sanitize("Entrén"));
        assertEquals("a", DataBundleExporter.sanitize("Å"));
        assertEquals("test-name", DataBundleExporter.sanitize("test-name"));
        assertEquals("abc123", DataBundleExporter.sanitize("abc123"));
        assertEquals("kassa", DataBundleExporter.sanitize("###"));
    }

    @Test
    void buildDefaultFileNameUsesEventIdWhenCashierSlugFallsBack() {
        assertEquals(
                "kassa-event-123-2026-03-25-080000.zip",
                DataBundleExporter.buildDefaultFileName("event-123", "###", "2026-03-25-080000")
        );
    }

    @Test
    void buildDefaultFileNameOmitsDuplicateFallbackSlug() {
        assertEquals(
                "kassa-2026-03-25-080000.zip",
                DataBundleExporter.buildDefaultFileName("###", "###", "2026-03-25-080000")
        );
    }

    @Test
    void createBundleProducesValidZip() throws Exception {
        String eventId = "test-event-001";
        Path eventDir = tempDir.resolve("events").resolve(eventId);
        Path configDir = tempDir.resolve("config");
        Path logsDir = tempDir.resolve("logs");
        Files.createDirectories(eventDir);
        Files.createDirectories(configDir);
        Files.createDirectories(logsDir);

        Files.writeString(eventDir.resolve("pending_items.jsonl"), "{\"itemId\":\"i1\"}\n");
        Files.writeString(eventDir.resolve("rejected_purchases.jsonl"), "{\"itemId\":\"i3\"}\n");
        Files.writeString(eventDir.resolve("iloppis_metadata.json"),
                "{\"eventId\":\"" + eventId + "\",\"apiKey\":\"event-secret\",\"eventName\":\"Test Loppis\"}\n");
        Files.writeString(configDir.resolve("global.json"), "{\"language\":\"sv\"}\n");
        Files.writeString(configDir.resolve("iloppis-mode.json"),
                "{\"eventId\":\"" + eventId + "\",\"apiKey\":\"root-secret\",\"apiBaseUrl\":\"https://example.test\"}\n");
        Files.writeString(logsDir.resolve("loppiskassan.log"), "test log\n");

        Path zipPath = tempDir.resolve("iloppis-support-test-2026-03-24-100000.zip");

        DataBundleExporter.createBundle(zipPath, eventId, "Test Loppis", "Kassa-1",
                eventDir, configDir, logsDir);

        assertTrue(Files.exists(zipPath));
        assertTrue(Files.size(zipPath) > 0);

        boolean hasManifest = false;
        boolean hasPending = false;
        boolean hasRejected = false;
        boolean hasEventMetadata = false;
        boolean hasGlobalConfig = false;
        boolean hasModeConfig = false;
        boolean hasLog = false;
        String manifestContent = null;
        String eventMetadataContent = null;
        String modeConfigContent = null;

        try (ZipInputStream zis = new ZipInputStream(
                Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                if ("manifest.json".equals(entry.getName())) {
                    hasManifest = true;
                    manifestContent = content;
                } else if ("event/pending_items.jsonl".equals(entry.getName())) {
                    hasPending = true;
                } else if ("event/rejected_purchases.jsonl".equals(entry.getName())) {
                    hasRejected = true;
                } else if ("event/iloppis_metadata.json".equals(entry.getName())) {
                    hasEventMetadata = true;
                    eventMetadataContent = content;
                } else if ("config/global.json".equals(entry.getName())) {
                    hasGlobalConfig = true;
                } else if ("config/iloppis-mode.json".equals(entry.getName())) {
                    hasModeConfig = true;
                    modeConfigContent = content;
                } else if ("logs/loppiskassan.log".equals(entry.getName())) {
                    hasLog = true;
                }
                zis.closeEntry();
            }
        }

        assertTrue(hasManifest, "ZIP should contain manifest.json");
        assertTrue(hasPending, "ZIP should contain event/pending_items.jsonl");
        assertTrue(hasRejected, "ZIP should contain event/rejected_purchases.jsonl");
        assertTrue(hasEventMetadata, "ZIP should contain event/iloppis_metadata.json");
        assertTrue(hasGlobalConfig, "ZIP should contain config/global.json");
        assertTrue(hasModeConfig, "ZIP should contain config/iloppis-mode.json");
        assertTrue(hasLog, "ZIP should contain logs/loppiskassan.log");
        assertNotNull(manifestContent);
        assertNotNull(eventMetadataContent);
        assertNotNull(modeConfigContent);
        assertTrue(manifestContent.contains("\"cashierName\": \"Kassa-1\""));
        assertTrue(manifestContent.contains("\"eventId\": \"" + eventId + "\""));
        assertTrue(manifestContent.contains("\"format\": \"iloppis-support-bundle-v1\""));
        assertTrue(manifestContent.contains("\"purpose\": \"support\""));
        assertTrue(eventMetadataContent.contains("\"eventId\": \"" + eventId + "\""));
        assertTrue(modeConfigContent.contains("\"eventId\": \"" + eventId + "\""));
        assertTrue(eventMetadataContent.contains("\"eventName\": \"Test Loppis\""));
        assertTrue(modeConfigContent.contains("\"apiBaseUrl\": \"https://example.test\""));
        assertTrue(!eventMetadataContent.contains("event-secret"));
        assertTrue(!modeConfigContent.contains("root-secret"));
        assertTrue(!eventMetadataContent.contains("apiKey"));
        assertTrue(!modeConfigContent.contains("apiKey"));
    }

    @Test
    void createBundleCreatesMissingParentDirectories() throws Exception {
        Path zipPath = tempDir.resolve("nested").resolve("exports").resolve("kassa-test.zip");
        Path eventDir = tempDir.resolve("events").resolve("event-1");
        Path configDir = tempDir.resolve("config");
        Path logsDir = tempDir.resolve("logs");

        Files.createDirectories(eventDir);
        Files.createDirectories(configDir);
        Files.createDirectories(logsDir);

        DataBundleExporter.createBundle(zipPath, "event-1", "Test Loppis", "Kassa-1",
                eventDir, configDir, logsDir);

        assertTrue(Files.exists(zipPath));
    }
}
