package se.goencoder.loppiskassan.service;

import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.SoldItemsServiceCreateSoldItemsBody;
import se.goencoder.iloppis.model.V1CreateSoldItemsResponse;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.storage.PendingItemsStore;
import se.goencoder.loppiskassan.utils.RejectedItemsHelper;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

/**
 * Background service that periodically attempts to upload pending items.
 * Runs when in iLoppis mode and stops on mode change or app exit.
 */
public class BackgroundSyncManager {
    
    private static final Logger log = Logger.getLogger(BackgroundSyncManager.class.getName());
    private static final long SYNC_INTERVAL_MS = 30_000; // 30 seconds
    
    private static BackgroundSyncManager instance;
    private Timer syncTimer;
    private String activeEventId;
    private boolean isRunning = false;
    
    /** Listener interface for pending count changes. */
    public interface PendingCountListener {
        void onPendingCountChanged(int pendingCount);
    }
    
    private PendingCountListener pendingCountListener;
    
    private BackgroundSyncManager() {
        // Private constructor for singleton
    }
    
    public static synchronized BackgroundSyncManager getInstance() {
        if (instance == null) {
            instance = new BackgroundSyncManager();
        }
        return instance;
    }
    
    /**
     * Start background sync for the given event.
     * 
     * @param eventId the event ID to sync pending items for
     */
    public synchronized void start(String eventId) {
        if (isRunning && eventId.equals(activeEventId)) {
            log.fine("Background sync already running for event: " + eventId);
            return;
        }
        
        stop(); // Stop any existing timer
        
        this.activeEventId = eventId;
        this.isRunning = true;
        
        log.info("Starting background sync for event: " + eventId);
        
        syncTimer = new Timer("BackgroundSync-" + eventId, true); // daemon thread
        syncTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                attemptSync();
            }
        }, SYNC_INTERVAL_MS, SYNC_INTERVAL_MS);
    }
    
    /**
     * Stop background sync.
     */
    public synchronized void stop() {
        if (syncTimer != null) {
            log.info("Stopping background sync");
            syncTimer.cancel();
            syncTimer = null;
        }
        isRunning = false;
        activeEventId = null;
    }
    
    /**
     * Check if background sync is running.
     */
    public synchronized boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Set listener for pending count changes.
     * Called on EDT when pending count changes.
     */
    public void setPendingCountListener(PendingCountListener listener) {
        this.pendingCountListener = listener;
    }
    
    /**
     * Get the current count of pending (non-uploaded) items for the active event.
     * Returns 0 if no event is active or on any error.
     */
    public int getPendingCount() {
        String eventId = activeEventId;
        if (eventId == null) return 0;
        try {
            PendingItemsStore store = new PendingItemsStore(eventId);
            return store.readPending().size();
        } catch (IOException e) {
            return 0;
        }
    }
    
    public void notifyPendingCountChanged() {
        if (pendingCountListener != null) {
            int count = getPendingCount();
            SwingUtilities.invokeLater(() -> pendingCountListener.onPendingCountChanged(count));
        }
    }
    
    /**
     * Attempt to upload pending items for the active event.
     * Called periodically by the timer.
     */
    private void attemptSync() {
        if (activeEventId == null) {
            return;
        }
        
        try {
            Path pendingPath = LocalEventPaths.getPendingItemsPath(activeEventId);
            
            // Check if pending file exists and has content
            if (!Files.exists(pendingPath) || Files.size(pendingPath) == 0) {
                return; // Nothing to sync
            }
            
            log.info("Background sync: Found pending items, attempting upload");
            
            // Load pending items
            PendingItemsStore store = new PendingItemsStore(activeEventId);
            List<V1SoldItem> pendingItems = store.readPending();
            if (pendingItems.isEmpty()) {
                return;
            }
            
            // Attempt upload - convert domain items to API items
            SoldItemsServiceCreateSoldItemsBody createSoldItems = new SoldItemsServiceCreateSoldItemsBody();
            for (V1SoldItem item : pendingItems) {
                se.goencoder.iloppis.model.V1SoldItem apiItem = se.goencoder.loppiskassan.utils.SoldItemUtils.toApiSoldItem(item);
                createSoldItems.addItemsItem(apiItem);
            }
            
            V1CreateSoldItemsResponse response = ApiHelper.INSTANCE
                    .getSoldItemsServiceApi()
                    .soldItemsServiceCreateSoldItems(activeEventId, createSoldItems);
            
            int accepted = response.getAcceptedItems() != null ? response.getAcceptedItems().size() : 0;
            int rejected = response.getRejectedItems() != null ? response.getRejectedItems().size() : 0;
            
            log.info(() -> String.format("Background sync: Upload successful - %d accepted, %d rejected", 
                accepted, rejected));
            
            // Handle rejected items
            if (response.getRejectedItems() != null && !response.getRejectedItems().isEmpty()) {
                RejectedItemsHelper.saveRejectedItems(activeEventId, response.getRejectedItems());
            }
            
            // Mark all successfully uploaded items
            for (V1SoldItem item : pendingItems) {
                item.setUploaded(true);
            }
            
            // Save updated items back (preserving history, not deleting file)
            try {
                List<V1SoldItem> allItems = store.readAll();
                // Update uploaded status in the full list
                for (V1SoldItem allItem : allItems) {
                    for (V1SoldItem uploaded : pendingItems) {
                        if (allItem.getItemId().equals(uploaded.getItemId())) {
                            allItem.setUploaded(true);
                        }
                    }
                }
                store.saveAll(allItems);
            } catch (IOException ioEx) {
                log.warning("Background sync: Failed to update local file - " + ioEx.getMessage());
            }
            
            log.info("Background sync: Marked " + pendingItems.size() + " items as uploaded");
            
            // Notify UI on EDT
            notifyPendingCountChanged();
            
        } catch (ApiException e) {
            if (ApiHelper.isLikelyNetworkError(e)) {
                log.fine("Background sync: Network error, will retry later");
            } else {
                log.warning("Background sync: API error - " + e.getMessage());
            }
        } catch (Exception e) {
            log.warning("Background sync: Unexpected error - " + e.getMessage());
        }
    }
}
