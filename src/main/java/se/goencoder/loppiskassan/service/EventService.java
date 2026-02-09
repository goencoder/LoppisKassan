package se.goencoder.loppiskassan.service;

import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.loppiskassan.V1SoldItem;

import java.io.IOException;
import java.util.List;

/**
 * Strategy interface for event-related operations.
 * Implementations provide local-only or online-backed behavior.
 * 
 * This interface encapsulates the key operations that differ between
 * local and online modes, eliminating if(isLocal) branches from controllers.
 */
public interface EventService {

    /**
     * Save sold items after checkout.
     * Online mode: uploads to server then saves locally (with degraded mode fallback)
     * Local mode: saves locally only
     * 
     * @param eventId event identifier
     * @param items items to save
     * @throws IOException if local save fails
     * @throws ApiException if online upload fails
     */
    void saveSoldItems(String eventId, List<V1SoldItem> items) throws IOException, ApiException;

    /**
     * Mark items as paid out/collected by seller.
     * Online mode: calls API with filters then updates local file
     * Local mode: updates local file only (filters not used)
     * 
     * @param eventId event identifier
     * @param sellerFilter seller filter from UI (may be empty)
     * @param paymentMethodFilter payment method filter from UI (may be empty)
     * @throws ApiException if online API call fails
     */
    void performPayout(String eventId, String sellerFilter, String paymentMethodFilter) throws ApiException;

    /**
     * Synchronize/import items based on mode.
     * The service handles the orchestration: local runs import callback,
     * online runs upload/download in a progress dialog.
     * 
     * @param context provides callbacks and UI components for coordination
     * @throws ApiException if server operations fail
     * @throws IOException if file operations fail
     */
    void synchronizeItems(SyncContext context) throws ApiException, IOException;

    /**
     * Check if this is a local-only event service.
     * @return true if local mode
     */
    boolean isLocal();
}
