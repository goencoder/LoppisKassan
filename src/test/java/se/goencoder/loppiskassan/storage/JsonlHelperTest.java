package se.goencoder.loppiskassan.storage;

import org.junit.jupiter.api.Test;
import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
}
