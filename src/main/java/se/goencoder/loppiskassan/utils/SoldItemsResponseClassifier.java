package se.goencoder.loppiskassan.utils;

import se.goencoder.iloppis.model.V1CreateSoldItemsResponse;
import se.goencoder.iloppis.model.V1RejectedItem;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper for classifying sold item upload responses.
 * Separates accepted items, duplicates (idempotent), and actionable rejections.
 */
public final class SoldItemsResponseClassifier {
    private SoldItemsResponseClassifier() {}

    public record UploadOutcome(
            Set<String> acceptedItemIds,
            Set<String> duplicateItemIds,
            List<V1RejectedItem> rejectedItems
    ) {
        public Set<String> uploadedItemIds() {
            Set<String> uploaded = new HashSet<>(acceptedItemIds);
            uploaded.addAll(duplicateItemIds);
            return uploaded;
        }

        public Set<String> rejectedItemIds() {
            Set<String> rejectedIds = new HashSet<>();
            for (V1RejectedItem rejectedItem : rejectedItems) {
                if (rejectedItem.getItem() != null && rejectedItem.getItem().getItemId() != null) {
                    rejectedIds.add(rejectedItem.getItem().getItemId());
                }
            }
            return rejectedIds;
        }

        public boolean hasRejectedItems() {
            return !rejectedItems.isEmpty();
        }
    }

    public static UploadOutcome classify(V1CreateSoldItemsResponse response) {
        Set<String> accepted = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        List<V1RejectedItem> rejected = new ArrayList<>();

        if (response != null && response.getAcceptedItems() != null) {
            for (se.goencoder.iloppis.model.V1SoldItem acceptedItem : response.getAcceptedItems()) {
                if (acceptedItem.getItemId() != null) {
                    accepted.add(acceptedItem.getItemId());
                }
            }
        }

        if (response != null && response.getRejectedItems() != null) {
            for (V1RejectedItem rejectedItem : response.getRejectedItems()) {
                String itemId = null;
                if (rejectedItem.getItem() != null) {
                    itemId = rejectedItem.getItem().getItemId();
                }
                if (isDuplicate(rejectedItem)) {
                    if (itemId != null) {
                        duplicates.add(itemId);
                    }
                } else {
                    rejected.add(rejectedItem);
                }
            }
        }

        return new UploadOutcome(accepted, duplicates, rejected);
    }

    public static boolean isDuplicate(V1RejectedItem rejectedItem) {
        if (rejectedItem == null || rejectedItem.getErrorCode() == null) {
            return false;
        }
        return rejectedItem.getErrorCode().toString().contains("DUPLICATE");
    }

    public static boolean isInvalidSeller(V1RejectedItem rejectedItem) {
        if (rejectedItem == null || rejectedItem.getErrorCode() == null) {
            return false;
        }
        return rejectedItem.getErrorCode().toString().contains("INVALID_SELLER");
    }

    public static boolean isCollateral(V1RejectedItem rejectedItem) {
        if (rejectedItem == null) {
            return false;
        }
        String reason = rejectedItem.getReason();
        boolean noReason = reason == null || reason.isBlank();
        if (!noReason) {
            return false;
        }
        if (rejectedItem.getErrorCode() == null) {
            return true;
        }
        return rejectedItem.getErrorCode().toString().contains("UNSPECIFIED");
    }
}
