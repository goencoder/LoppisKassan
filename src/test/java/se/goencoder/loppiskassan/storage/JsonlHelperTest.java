package se.goencoder.loppiskassan.storage;

import org.junit.jupiter.api.Test;
import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JsonlHelperTest {

    @Test
    void roundTripJsonLine() {
        LocalDateTime soldTime = LocalDateTime.of(2026, 2, 8, 12, 30);
        LocalDateTime paidOutTime = LocalDateTime.of(2026, 2, 8, 13, 10);
        V1SoldItem item = new V1SoldItem(
                "purchase-1",
                "item-1",
                soldTime,
                42,
                150,
                paidOutTime,
                V1PaymentMethod.Kontant,
                true
        );

        String line = JsonlHelper.toJsonLine(item);
        V1SoldItem parsed = JsonlHelper.fromJsonLine(line);

        assertEquals(item.getItemId(), parsed.getItemId());
        assertEquals(item.getPurchaseId(), parsed.getPurchaseId());
        assertEquals(item.getSeller(), parsed.getSeller());
        assertEquals(item.getPrice(), parsed.getPrice());
        assertEquals(item.getPaymentMethod(), parsed.getPaymentMethod());
        assertEquals(item.getSoldTime(), parsed.getSoldTime());
        assertEquals(item.getCollectedBySellerTime(), parsed.getCollectedBySellerTime());
        assertEquals(item.isUploaded(), parsed.isUploaded());
    }

    @Test
    void appendAndReadItems() throws Exception {
        Path tempDir = Files.createTempDirectory("jsonl-test");
        Path file = tempDir.resolve("pending_items.jsonl");

        V1SoldItem item = new V1SoldItem(
                "purchase-2",
                "item-2",
                LocalDateTime.of(2026, 2, 8, 14, 0),
                7,
                75,
                null,
                V1PaymentMethod.Swish,
                false
        );

        JsonlHelper.appendItems(file, List.of(item));
        List<V1SoldItem> loaded = JsonlHelper.readItems(file);

        assertEquals(1, loaded.size());
        assertNotNull(loaded.get(0).getSoldTime());
        assertEquals(item.getItemId(), loaded.get(0).getItemId());
    }

    @Test
    void writeItemsUsesAtomicMove() throws Exception {
        Path tempDir = Files.createTempDirectory("jsonl-test");
        Path file = tempDir.resolve("pending_items.jsonl");
        Path tempFile = tempDir.resolve("pending_items.jsonl.tmp");

        V1SoldItem item = new V1SoldItem(
                "purchase-3", "item-3",
                LocalDateTime.of(2026, 3, 24, 10, 0),
                1, 200, null, V1PaymentMethod.Kontant, false
        );

        JsonlHelper.writeItems(file, List.of(item));

        assertTrue(Files.exists(file), "Target file should exist");
        assertFalse(Files.exists(tempFile), "Temp file should be removed after atomic move");

        List<V1SoldItem> loaded = JsonlHelper.readItems(file);
        assertEquals(1, loaded.size());
        assertEquals("item-3", loaded.get(0).getItemId());
    }

    @Test
    void writeItemsEmptyListCreatesEmptyFile() throws Exception {
        Path tempDir = Files.createTempDirectory("jsonl-test");
        Path file = tempDir.resolve("pending_items.jsonl");

        // Write some items first
        V1SoldItem item = new V1SoldItem(
                "purchase-4", "item-4",
                LocalDateTime.of(2026, 3, 24, 10, 0),
                1, 100, null, V1PaymentMethod.Swish, true
        );
        JsonlHelper.writeItems(file, List.of(item));
        assertEquals(1, JsonlHelper.readItems(file).size());

        // Overwrite with empty list
        JsonlHelper.writeItems(file, List.of());
        assertTrue(Files.exists(file), "File should still exist");
        assertEquals(0, JsonlHelper.readItems(file).size());
    }

    @Test
    void appendItemsFsyncsToFile() throws Exception {
        Path tempDir = Files.createTempDirectory("jsonl-test");
        Path file = tempDir.resolve("pending_items.jsonl");

        V1SoldItem item1 = new V1SoldItem(
                "purchase-5", "item-5",
                LocalDateTime.of(2026, 3, 24, 10, 0),
                2, 50, null, V1PaymentMethod.Kontant, false
        );
        V1SoldItem item2 = new V1SoldItem(
                "purchase-5", "item-6",
                LocalDateTime.of(2026, 3, 24, 10, 0),
                3, 75, null, V1PaymentMethod.Kontant, false
        );

        JsonlHelper.appendItems(file, List.of(item1));
        JsonlHelper.appendItems(file, List.of(item2));

        List<V1SoldItem> loaded = JsonlHelper.readItems(file);
        assertEquals(2, loaded.size());
        assertEquals("item-5", loaded.get(0).getItemId());
        assertEquals("item-6", loaded.get(1).getItemId());
    }
}
