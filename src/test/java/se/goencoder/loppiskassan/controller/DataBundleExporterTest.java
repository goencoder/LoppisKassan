package se.goencoder.loppiskassan.controller;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.storage.JsonlHelper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

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
    void createBundleProducesValidZip() throws Exception {
        // Setup: create test items
        String eventId = "test-event-001";
        Path eventDir = tempDir.resolve("events").resolve(eventId);
        Files.createDirectories(eventDir);

        V1SoldItem item1 = new V1SoldItem("p1", "i1",
                LocalDateTime.of(2026, 3, 24, 10, 0), 1, 100, null,
                V1PaymentMethod.Kontant, false);
        V1SoldItem item2 = new V1SoldItem("p1", "i2",
                LocalDateTime.of(2026, 3, 24, 10, 1), 2, 200, null,
                V1PaymentMethod.Swish, true);

        // Create rejected file on disk (read by createBundle)
        Path rejectedPath = eventDir.resolve("rejected_purchases.jsonl");
        V1SoldItem rejected = new V1SoldItem("p2", "i3",
                LocalDateTime.of(2026, 3, 24, 11, 0), 99, 50, null,
                V1PaymentMethod.Kontant, false);
        JsonlHelper.appendItems(rejectedPath, List.of(rejected));

        // Create bundle with explicit rejected path
        Path zipPath = tempDir.resolve("kassa-test-2026-03-24-100000.zip");

        DataBundleExporter.createBundle(zipPath, eventId, "Test Loppis", "Kassa-1",
                List.of(item1, item2), rejectedPath);

        // Verify ZIP exists and has correct entries
        assertTrue(Files.exists(zipPath));
        assertTrue(Files.size(zipPath) > 0);

        // Read ZIP contents
        boolean hasManifest = false;
        boolean hasPending = false;
        boolean hasRejected = false;
        String manifestContent = null;

        try (ZipInputStream zis = new ZipInputStream(
                Files.newInputStream(zipPath), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                if ("manifest.json".equals(entry.getName())) {
                    hasManifest = true;
                    manifestContent = content;
                } else if ("pending_items.jsonl".equals(entry.getName())) {
                    hasPending = true;
                } else if ("rejected_purchases.jsonl".equals(entry.getName())) {
                    hasRejected = true;
                }
                zis.closeEntry();
            }
        }

        assertTrue(hasManifest, "ZIP should contain manifest.json");
        assertTrue(hasPending, "ZIP should contain pending_items.jsonl");
        assertTrue(hasRejected, "ZIP should contain rejected_purchases.jsonl");
        assertNotNull(manifestContent);
        assertTrue(manifestContent.contains("\"cashierName\": \"Kassa-1\""));
        assertTrue(manifestContent.contains("\"itemCount\": 2"));
        assertTrue(manifestContent.contains("\"totalRevenue\": 300"));
        assertTrue(manifestContent.contains("\"format\": \"loppiskassan-bundle-v1\""));
        assertTrue(manifestContent.contains("\"uploadedCount\": 1"));
        assertTrue(manifestContent.contains("\"pendingCount\": 1"));
    }

    @Test
    void createBundleCreatesMissingParentDirectories() throws Exception {
        Path zipPath = tempDir.resolve("nested").resolve("exports").resolve("kassa-test.zip");

        V1SoldItem item = new V1SoldItem("p1", "i1",
                LocalDateTime.of(2026, 3, 24, 10, 0), 1, 100, null,
                V1PaymentMethod.Kontant, false);

        DataBundleExporter.createBundle(zipPath, "event-1", "Test Loppis", "Kassa-1",
                List.of(item), null);

        assertTrue(Files.exists(zipPath));
    }
}
