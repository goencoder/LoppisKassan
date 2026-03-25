package se.goencoder.loppiskassan.util;

import se.goencoder.loppiskassan.V1SoldItem;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class RecentPurchases {
    private RecentPurchases() {}

    public record PurchaseGroup(String purchaseId, LocalDateTime soldTime, List<V1SoldItem> items, int totalAmount) {}

    public static List<PurchaseGroup> latest(List<V1SoldItem> items, int limit) {
        if (items == null || items.isEmpty() || limit <= 0) {
            return List.of();
        }

        Map<String, GroupAccumulator> grouped = new LinkedHashMap<>();
        int index = 0;
        for (V1SoldItem item : items) {
            int currentIndex = index;
            String purchaseId = item.getPurchaseId();
            if (purchaseId == null || purchaseId.isBlank()) {
                purchaseId = item.getItemId();
            }
            String purchaseKey = purchaseId;
            grouped.computeIfAbsent(purchaseKey, ignored -> new GroupAccumulator(purchaseKey))
                    .add(item, currentIndex);
            index++;
        }

        return grouped.values().stream()
                .sorted(Comparator
                        .comparing(GroupAccumulator::soldTime, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(GroupAccumulator::lastSeenIndex, Comparator.reverseOrder()))
                .limit(limit)
                .map(GroupAccumulator::toGroup)
                .toList();
    }

    private static final class GroupAccumulator {
        private final String purchaseId;
        private final List<V1SoldItem> items = new ArrayList<>();
        private LocalDateTime soldTime;
        private int totalAmount;
        private int lastSeenIndex = -1;

        private GroupAccumulator(String purchaseId) {
            this.purchaseId = purchaseId;
        }

        private void add(V1SoldItem item, int itemIndex) {
            items.add(item);
            totalAmount += item.getPrice();
            if (item.getSoldTime() != null && (soldTime == null || item.getSoldTime().isAfter(soldTime))) {
                soldTime = item.getSoldTime();
            }
            lastSeenIndex = itemIndex;
        }

        private LocalDateTime soldTime() {
            return soldTime;
        }

        private int lastSeenIndex() {
            return lastSeenIndex;
        }

        private PurchaseGroup toGroup() {
            return new PurchaseGroup(purchaseId, soldTime, List.copyOf(items), totalAmount);
        }
    }
}
