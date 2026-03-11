package se.goencoder.loppiskassan.service;

import se.goencoder.loppiskassan.V1SoldItem;

import java.util.List;

/**
 * Strategy interface for history tab operations.
 * Eliminates if-checks by delegating mode-specific behavior to concrete implementations.
 * 
 * Operations:
 * - Payout: Mark items as collected by seller (local-only)
 * - Archive: Move paid items to archive file (local-only)
 * - Sync: Import/export data (local = CSV import, online = API upload/download)
 */
public interface HistoryOperations {
    
    /**
     * Perform payout operation - mark filtered items as collected.
     * @param filteredItems Items to mark as paid out
     */
    void performPayout(List<V1SoldItem> filteredItems);
    
    /**
     * Archive paid items.
     * @param paidItems Items to archive
     * @return true if archive succeeded
     */
    boolean performArchive(List<V1SoldItem> paidItems);
    
    /**
     * Synchronize data (import for local, upload/download for online).
     * @param syncCallback Callback with mode-specific logic
     */
    void performSync(Runnable syncCallback);
    
    /**
     * Check if payout operation is available in this mode.
     */
    boolean isPayoutAvailable();
    
    /**
     * Check if archive operation is available in this mode.
     */
    boolean isArchiveAvailable();
}
