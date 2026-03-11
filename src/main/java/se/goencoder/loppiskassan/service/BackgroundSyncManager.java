package se.goencoder.loppiskassan.service;

import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.SoldItemsServiceCreateSoldItemsBody;
import se.goencoder.iloppis.model.V1CreateSoldItemsResponse;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.rest.AuthErrorHandler;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.storage.PendingItemsStore;
import se.goencoder.loppiskassan.utils.RejectedItemsHelper;
import se.goencoder.loppiskassan.utils.SoldItemsResponseClassifier;
import se.goencoder.loppiskassan.utils.UlidGenerator;

import javax.swing.SwingUtilities;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Background service that persists sold items locally and uploads them in the background.
 * <p>
 * Design goals:
 * <ul>
 *   <li>Local-first: items are always written to disk before any upload attempt.</li>
 *   <li>Single-threaded I/O: all writes/reads to the pending JSONL file and all API uploads
 *       happen on ONE background thread to avoid races and lost updates.</li>
 *   <li>Unified pipeline: the same upload logic (classify + retry + update file) is used
 *       regardless of what triggers the sync.</li>
 * </ul>
 *
 * This class owns the "sync thread". Any code path that needs to mutate the pending
 * file MUST go through this class.
 */
public class BackgroundSyncManager {

    private static final Logger log = Logger.getLogger(BackgroundSyncManager.class.getName());
    private static final long SYNC_INTERVAL_MS = 30_000; // 30 seconds

    private static BackgroundSyncManager instance;

    /**
     * The single-threaded executor that performs ALL file I/O and uploads.
     */
    private ScheduledExecutorService syncExecutor;
    private ScheduledFuture<?> periodicTask;
    private volatile Thread syncThread;
    private final Queue<List<V1SoldItem>> pendingQueue = new ConcurrentLinkedQueue<>();

    private String activeEventId;
    private boolean isRunning = false;

    /** Listener interface for pending count changes. */
    public interface PendingCountListener {
        void onPendingCountChanged(int pendingCount);
    }

    private PendingCountListener pendingCountListener;

    /**
     * Result summary from a sync run.
     * Used by manual sync actions (e.g. History "Uppdatera web").
     */
    public record SyncResult(
            int accepted,
            int duplicates,
            int rejected,
            boolean networkError,
            boolean authError,
            boolean fileError
    ) {
        public static SyncResult empty() {
            return new SyncResult(0, 0, 0, false, false, false);
        }
    }

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
     * Creates a single-threaded executor that owns all file I/O + upload.
     *
     * @param eventId the event ID to sync pending items for
     */
    public synchronized void start(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        if (isRunning && eventId.equals(activeEventId)) {
            triggerSyncNow();
            return;
        }

        stop();

        this.activeEventId = eventId;
        this.isRunning = true;

        syncExecutor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "BackgroundSync-" + eventId);
            thread.setDaemon(true);
            syncThread = thread;
            return thread;
        });

        log.info("Starting background sync for event: " + eventId);

        periodicTask = syncExecutor.scheduleWithFixedDelay(
                this::syncOnceSafely,
                SYNC_INTERVAL_MS,
                SYNC_INTERVAL_MS,
                TimeUnit.MILLISECONDS
        );

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
     * Stop background sync and clear state.
     */
    public synchronized void stop() {
        if (periodicTask != null) {
            periodicTask.cancel(false);
            periodicTask = null;
        }
        if (syncExecutor != null) {
            syncExecutor.shutdownNow();
            syncExecutor = null;
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
     * Enqueue items for local persistence and background upload.
     * The method returns only after items are durably written to pending JSONL.
     *
     * NOTE: File I/O still runs on the sync thread to preserve single-writer semantics.
     */
    public void enqueueItems(String eventId, List<V1SoldItem> items) throws IOException {
        if (eventId == null || eventId.isBlank() || items == null || items.isEmpty()) {
            return;
        }
        ensureRunning(eventId);
        pendingQueue.add(new ArrayList<>(items));
        runOnSyncThread(() -> {
            flushQueueToDisk(eventId);
            return null;
        });
        notifyPendingCountChanged();
        triggerSyncNow();
    }

    /**
     * Persist all items to the pending file on the sync thread (blocking).
     * Used by manual operations like History sync or imports.
     */
    public void savePendingItems(String eventId, List<V1SoldItem> items) throws IOException {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        ensureRunning(eventId);
        runOnSyncThread(() -> {
            PendingItemsStore store = new PendingItemsStore(eventId);
            store.saveAll(items);
            return null;
        });
        notifyPendingCountChanged();
    }

    /**
     * Insert or replace a single item in the pending file (blocking).
     * This is used when a rejected item is edited and re-queued.
     */
    public void upsertPendingItem(String eventId, V1SoldItem item) throws IOException {
        if (eventId == null || eventId.isBlank() || item == null) {
            return;
        }
        ensureRunning(eventId);
        runOnSyncThread(() -> {
            PendingItemsStore store = new PendingItemsStore(eventId);
            List<V1SoldItem> allItems = store.readAll();
            boolean updatedExisting = false;
            for (int i = 0; i < allItems.size(); i++) {
                V1SoldItem existing = allItems.get(i);
                if (existing.getItemId() != null && existing.getItemId().equals(item.getItemId())) {
                    allItems.set(i, item);
                    updatedExisting = true;
                }
            }
            if (updatedExisting) {
                store.saveAll(allItems);
            } else {
                store.appendItems(List.of(item));
            }
            return null;
        });
        notifyPendingCountChanged();
    }

    /**
     * Trigger a best-effort sync attempt immediately on the sync thread.
     */
    public void triggerSyncNow() {
        ScheduledExecutorService executor = syncExecutor;
        if (executor == null) {
            return;
        }
        executor.submit(this::syncOnceSafely);
    }

    /**
     * Run a sync cycle and wait for completion. Intended for manual actions (e.g. History).
     */
    public SyncResult syncNowBlocking() {
        String eventId = activeEventId;
        if (eventId == null || eventId.isBlank()) {
            return SyncResult.empty();
        }
        ensureRunning(eventId);
        try {
            return runOnSyncThread(this::syncOnceInternal);
        } catch (IOException e) {
            return new SyncResult(0, 0, 0, false, false, true);
        }
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

    private void syncOnceSafely() {
        try {
            syncOnceInternal();
        } catch (Exception e) {
            log.warning("Background sync: Unexpected error - " + e.getMessage());
        }
    }

    /**
     * Sync cycle. Runs ONLY on the sync thread.
     *
     * Steps:
     * 1) Flush queued items to disk (local-first)
     * 2) Read pending items
     * 3) Upload + classify + retry collateral
     * 4) Update pending file, append rejected
     */
    private SyncResult syncOnceInternal() throws IOException {
        String eventId = activeEventId;
        if (eventId == null || eventId.isBlank()) {
            return SyncResult.empty();
        }

        flushQueueToDisk(eventId);

        Path pendingPath = LocalEventPaths.getPendingItemsPath(eventId);
        if (!Files.exists(pendingPath) || Files.size(pendingPath) == 0) {
            return SyncResult.empty();
        }

        PendingItemsStore store = new PendingItemsStore(eventId);
        List<V1SoldItem> pendingItems = store.readPending();
        if (pendingItems.isEmpty()) {
            return SyncResult.empty();
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

        boolean networkError = false;
        boolean authError = false;

        for (List<V1SoldItem> purchaseItems : purchaseGroups.values()) {
            try {
                SoldItemsResponseClassifier.UploadOutcome outcome = uploadPurchaseGroupWithRetry(eventId, purchaseItems);
                acceptedIds.addAll(outcome.acceptedItemIds());
                duplicateIds.addAll(outcome.duplicateItemIds());
                rejectedItems.addAll(outcome.rejectedItems());
            } catch (ApiException e) {
                if (AuthErrorHandler.isAuthError(e)) {
                    AuthErrorHandler.handleAuthStatus(e.getCode());
                    authError = true;
                } else if (ApiHelper.isLikelyNetworkError(e)) {
                    networkError = true;
                } else {
                    log.warning("Background sync: API error - " + e.getMessage());
                }
                break; // stop this sync cycle on any API failure
            }
        }

        if (!rejectedItems.isEmpty()) {
            RejectedItemsHelper.saveRejectedItems(eventId, rejectedItems);
        }

        if (!acceptedIds.isEmpty() || !duplicateIds.isEmpty() || !rejectedItems.isEmpty()) {
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
        }

        notifyPendingCountChanged();
        RejectedItemsManager.getInstance().notifyRejectedCountChanged(eventId);

        return new SyncResult(
                acceptedIds.size(),
                duplicateIds.size(),
                rejectedItems.size(),
                networkError,
                authError,
                false
        );
    }

    /**
     * Append queued items to disk before any upload attempt.
     * This is the "local-first" guarantee.
     */
    private void flushQueueToDisk(String eventId) throws IOException {
        List<V1SoldItem> drained = new ArrayList<>();
        List<V1SoldItem> batch;
        while ((batch = pendingQueue.poll()) != null) {
            drained.addAll(batch);
        }
        if (drained.isEmpty()) {
            return;
        }
        PendingItemsStore store = new PendingItemsStore(eventId);
        store.appendItems(drained);
    }

    private <T> T runOnSyncThread(Callable<T> task) throws IOException {
        ScheduledExecutorService executor = syncExecutor;
        if (executor == null) {
            try {
                return task.call();
            } catch (Exception e) {
                throw unwrapIo(e);
            }
        }
        if (Thread.currentThread() == syncThread) {
            try {
                return task.call();
            } catch (Exception e) {
                throw unwrapIo(e);
            }
        }
        try {
            return executor.submit(task).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        } catch (ExecutionException e) {
            throw unwrapIo(e.getCause());
        }
    }

    private IOException unwrapIo(Throwable throwable) {
        if (throwable instanceof IOException io) {
            return io;
        }
        return new IOException(throwable);
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
