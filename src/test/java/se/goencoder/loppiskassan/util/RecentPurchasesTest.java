package se.goencoder.loppiskassan.util;

import org.junit.jupiter.api.Test;
import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecentPurchasesTest {

    @Test
    void returnsLatestTenPurchasesGroupedByPurchaseId() {
        List<V1SoldItem> items = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            String purchaseId = "purchase-" + i;
            items.add(item(purchaseId, "item-" + i + "-1", LocalDateTime.of(2026, 3, 25, 10, i), 10, i * 10));
            items.add(item(purchaseId, "item-" + i + "-2", LocalDateTime.of(2026, 3, 25, 10, i), 11, i * 10 + 5));
        }

        List<RecentPurchases.PurchaseGroup> groups = RecentPurchases.latest(items, 10);

        assertEquals(10, groups.size());
        assertEquals("purchase-12", groups.getFirst().purchaseId());
        assertEquals("purchase-3", groups.getLast().purchaseId());
        assertEquals(2, groups.getFirst().items().size());
        assertEquals(245, groups.getFirst().totalAmount());
    }

    @Test
    void fallsBackToItemIdWhenPurchaseIdIsMissing() {
        List<V1SoldItem> items = List.of(
                item("", "item-1", LocalDateTime.of(2026, 3, 25, 11, 0), 10, 50),
                item(null, "item-2", LocalDateTime.of(2026, 3, 25, 11, 1), 11, 75)
        );

        List<RecentPurchases.PurchaseGroup> groups = RecentPurchases.latest(items, 10);

        assertEquals(2, groups.size());
        assertEquals("item-2", groups.getFirst().purchaseId());
        assertEquals("item-1", groups.getLast().purchaseId());
    }

    private V1SoldItem item(String purchaseId, String itemId, LocalDateTime soldTime, int seller, int price) {
        return new V1SoldItem(purchaseId, itemId, soldTime, seller, price, null, V1PaymentMethod.Kontant, true);
    }
}
