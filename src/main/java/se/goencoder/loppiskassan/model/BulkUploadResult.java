package se.goencoder.loppiskassan.model;

import se.goencoder.iloppis.model.V1RejectedItem;
import se.goencoder.loppiskassan.V1SoldItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the outcome of a bulk-upload operation.
 * Tracks accepted items, failed items, and duplicate items separately.
 * Duplicates are NOT errors — they indicate idempotent re-upload safety.
 */
public class BulkUploadResult {
    public final List<V1SoldItem> acceptedItems;
    public final List<V1RejectedItem> failedItems;
    public final List<V1RejectedItem> duplicateItems;
    public final List<String> errorMessages;

    public BulkUploadResult() {
        this.acceptedItems = new ArrayList<>();
        this.failedItems = new ArrayList<>();
        this.duplicateItems = new ArrayList<>();
        this.errorMessages = new ArrayList<>();
    }

    /**
     * Add an error message (e.g., network error, permission denied)
     */
    public void addError(String message) {
        this.errorMessages.add(message);
    }

    /**
     * Check if upload was completely successful (all items accepted, no duplicates, no errors)
     */
    public boolean isFullSuccess() {
        return !acceptedItems.isEmpty() 
            && failedItems.isEmpty() 
            && duplicateItems.isEmpty() 
            && errorMessages.isEmpty();
    }

    /**
     * Check if upload had partial success (some accepted, but some duplicates or failures)
     */
    public boolean isPartialSuccess() {
        return !acceptedItems.isEmpty() 
            && (!failedItems.isEmpty() || !duplicateItems.isEmpty() || !errorMessages.isEmpty());
    }

    /**
     * Check if upload completely failed (no accepted items)
     */
    public boolean isCompleteFailure() {
        return acceptedItems.isEmpty() && !errorMessages.isEmpty();
    }

    /**
     * Check if upload produced any results at all
     */
    public boolean hasResults() {
        return !acceptedItems.isEmpty() 
            || !failedItems.isEmpty() 
            || !duplicateItems.isEmpty();
    }

    /**
     * Get total number of items in result (across all categories)
     */
    public int getTotalItemCount() {
        return acceptedItems.size() + failedItems.size() + duplicateItems.size();
    }

    /**
     * Get summary text for display to user
     */
    public String getSummaryText() {
        if (isFullSuccess()) {
            return String.format("✅ %d items uploaded successfully", acceptedItems.size());
        }
        if (isPartialSuccess()) {
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("✅ %d items accepted\n", acceptedItems.size()));
            if (!duplicateItems.isEmpty()) {
                sb.append(String.format("⚠️ %d items already uploaded (duplicates)\n", duplicateItems.size()));
            }
            if (!failedItems.isEmpty()) {
                sb.append(String.format("❌ %d items failed\n", failedItems.size()));
            }
            return sb.toString();
        }
        if (!errorMessages.isEmpty()) {
            return String.format("❌ Upload failed: %s", 
                String.join("; ", errorMessages.stream().limit(1).toList()));
        }
        return "No results";
    }
}
