package se.goencoder.loppiskassan.service;

import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.SoldItemsServiceCreateSoldItemsBody;
import se.goencoder.iloppis.model.V1CreateSoldItemsResponse;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.storage.PendingItemsStore;
import se.goencoder.loppiskassan.utils.RejectedItemsHelper;
import se.goencoder.loppiskassan.utils.SoldItemsResponseClassifier;
import se.goencoder.loppiskassan.utils.UlidGenerator;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;
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
            triggerSyncNow();
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
        triggerSyncNow();
    }

    /**
     * Ensure background sync is running for the given event, and trigger an immediate attempt.
     */
    public synchronized void ensureRunning(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        if (!isRunning || !eventId.equals(activeEventId)) {
            start(eventId);
            return;
        }
        triggerSyncNow();
    }

    /**
     * Trigger a best-effort sync attempt immediately on a background thread.
     */
    public void triggerSyncNow() {
        if (activeEventId == null) {
            return;
        }
        new Thread(this::attemptSync, "BackgroundSync-Now").start();
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
            
            Map<String, List<V1SoldItem>> purchaseGroups = pendingItems.stream()
                    .collect(Collectors.groupingBy(item -> {
                        String purchaseId = item.getPurchaseId();
                        if (purchaseId == null || purchaseId.isBlank()) {
                            purchaseId = UlidGenerator.generate();
                            item.setPurchaseId(purchaseId);
                        }
                        return purchaseId;
                    }));

            HashSet<String> acceptedIds = new HashSet<>();
            HashSet<String> duplicateIds = new HashSet<>();
            List<se.goencoder.iloppis.model.V1RejectedItem> rejectedItems = new ArrayList<>();

            try {
                for (List<V1SoldItem> purchaseItems : purchaseGroups.values()) {
                    SoldItemsResponseClassifier.UploadOutcome outcome = uploadPurchaseGroupWithRetry(activeEventId, purchaseItems);
                    acceptedIds.addAll(outcome.acceptedItemIds());
                    duplicateIds.addAll(outcome.duplicateItemIds());
                    rejectedItems.addAll(outcome.rejectedItems());
                }
            } catch (ApiException e) {
                if (ApiHelper.isLikelyNetworkError(e)) {
                    log.fine("Background sync: Network error, will retry later");
                } else {
                    log.warning("Background sync: API error - " + e.getMessage());
                }
            }

            int accepted = acceptedIds.size();
            int rejected = rejectedItems.size();
            int duplicates = duplicateIds.size();

            log.info(() -> String.format(
                    "Background sync: Upload processed - %d accepted, %d duplicates, %d rejected",
                    accepted, duplicates, rejected));

            if (!rejectedItems.isEmpty()) {
                RejectedItemsHelper.saveRejectedItems(activeEventId, rejectedItems);
            }

            if (!acceptedIds.isEmpty() || !duplicateIds.isEmpty() || !rejectedItems.isEmpty()) {
                try {
                    List<V1SoldItem> allItems = store.readAll();
                    List<V1SoldItem> updatedItems = new ArrayList<>();
                    HashSet<String> rejectedIds = new HashSet<>();
                    for (se.goencoder.iloppis.model.V1RejectedItem rejectedItem : rejectedItems) {
                        if (rejectedItem.getItem() != null && rejectedItem.getItem().getItemId() != null) {
                            rejectedIds.add(rejectedItem.getItem().getItemId());
                        }
                    }
                    HashSet<String> uploadedIds = new HashSet<>(acceptedIds);
                    uploadedIds.addAll(duplicateIds);

                    for (V1SoldItem allItem : allItems) {
                        if (allItem.getItemId() == null) {
                            updatedItems.add(allItem);
                            continue;
                        }
                        if (rejectedIds.contains(allItem.getItemId())) {
                            continue;
                        }
                        if (uploadedIds.contains(allItem.getItemId())) {
                            allItem.setUploaded(true);
                        }
                        updatedItems.add(allItem);
                    }
                    store.saveAll(updatedItems);
                } catch (IOException ioEx) {
                    log.warning("Background sync: Failed to update local file - " + ioEx.getMessage());
                }
            }

            log.info("Background sync: Upload processing complete");

            // Notify UI on EDT
            notifyPendingCountChanged();
            RejectedItemsManager.getInstance().notifyRejectedCountChanged(activeEventId);
            
        } catch (Exception e) {
            log.warning("Background sync: Unexpected error - " + e.getMessage());
        }
    }

    private SoldItemsResponseClassifier.UploadOutcome uploadPurchaseGroupWithRetry(String eventId, List<V1SoldItem> purchaseItems) throws ApiException {
        V1CreateSoldItemsResponse response = uploadItemsToApi(eventId, purchaseItems);
        SoldItemsResponseClassifier.UploadOutcome outcome = SoldItemsResponseClassifier.classify(response);

        List<se.goencoder.iloppis.model.V1RejectedItem> rejected = new ArrayList<>(outcome.rejectedItems());
        HashSet<String> accepted = new HashSet<>(outcome.acceptedItemIds());
        HashSet<String> duplicates = new HashSet<>(outcome.duplicateItemIds());

        boolean hasInvalidSeller = rejected.stream().anyMatch(SoldItemsResponseClassifier::isInvalidSeller);
        if (!hasInvalidSeller) {
            return outcome;
        }

        HashSet<String> collateralIds = rejected.stream()
                .filter(SoldItemsResponseClassifier::isCollateral)
                .map(BackgroundSyncManager::getRejectedItemId)
                .filter(itemId -> itemId != null && !itemId.isBlank())
                .collect(Collectors.toCollection(HashSet::new));

        if (collateralIds.isEmpty()) {
            return outcome;
        }

        List<V1SoldItem> retryItems = purchaseItems.stream()
                .filter(item -> item.getItemId() != null && collateralIds.contains(item.getItemId()))
                .collect(Collectors.toList());

        if (retryItems.isEmpty()) {
            return outcome;
        }

        rejected.removeIf(rejectedItem -> {
            String itemId = getRejectedItemId(rejectedItem);
            return itemId != null && collateralIds.contains(itemId);
        });

        V1CreateSoldItemsResponse retryResponse = uploadItemsToApi(eventId, retryItems);
        SoldItemsResponseClassifier.UploadOutcome retryOutcome = SoldItemsResponseClassifier.classify(retryResponse);

        accepted.addAll(retryOutcome.acceptedItemIds());
        duplicates.addAll(retryOutcome.duplicateItemIds());
        rejected.addAll(retryOutcome.rejectedItems());

        return new SoldItemsResponseClassifier.UploadOutcome(accepted, duplicates, rejected);
    }

    private V1CreateSoldItemsResponse uploadItemsToApi(String eventId, List<V1SoldItem> items) throws ApiException {
        SoldItemsServiceCreateSoldItemsBody requestBody = new SoldItemsServiceCreateSoldItemsBody();
        for (V1SoldItem item : items) {
            se.goencoder.iloppis.model.V1SoldItem apiItem = se.goencoder.loppiskassan.utils.SoldItemUtils.toApiSoldItem(item);
            if (item.getSoldTime() != null) {
                apiItem.setSoldTime(OffsetDateTime.of(item.getSoldTime(), OffsetDateTime.now().getOffset()));
            }
            requestBody.addItemsItem(apiItem);
        }
        return ApiHelper.INSTANCE
                .getSoldItemsServiceApi()
                .soldItemsServiceCreateSoldItems(eventId, requestBody);
    }

    private static String getRejectedItemId(se.goencoder.iloppis.model.V1RejectedItem rejectedItem) {
        if (rejectedItem == null || rejectedItem.getItem() == null) {
            return null;
        }
        return rejectedItem.getItem().getItemId();
    }
}
