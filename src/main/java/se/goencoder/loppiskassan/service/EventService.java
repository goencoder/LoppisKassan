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
     * Online mode: calls API then updates local file
     * Local mode: updates local file only
     * 
     * @throws ApiException if online API call fails
     */
    void performPayout() throws ApiException;

    /**
     * Handle import action.
     * Online mode: syncs with server (upload unsent, download new)
     * Local mode: imports from selected files
     * 
     * @throws ApiException if server sync fails
     * @throws IOException if file operations fail
     */
    void handleImport() throws ApiException, IOException;

    /**
     * Check if this is a local-only event service.
     * @return true if local mode
     */
    boolean isLocal();
}
