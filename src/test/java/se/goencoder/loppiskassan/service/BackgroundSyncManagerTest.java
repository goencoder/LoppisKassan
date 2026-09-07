package se.goencoder.loppiskassan.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.goencoder.iloppis.model.V1CreateSoldItemsResponse;
import se.goencoder.iloppis.model.V1RejectedItem;
import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.storage.PendingItemsStore;
import se.goencoder.loppiskassan.storage.RejectedItemsStore;
import se.goencoder.loppiskassan.utils.SoldItemUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackgroundSyncManagerTest {
    @TempDir Path directory;
    private String previousBase;
    private static final String EVENT = "synthetic-sync-test";

    @BeforeEach void isolateStorage() {
        previousBase = System.getProperty("loppiskassan.base.dir");
        System.setProperty("loppiskassan.base.dir", directory.toString());
    }

    @AfterEach void restoreStorage() {
        if (previousBase == null) System.clearProperty("loppiskassan.base.dir");
        else System.setProperty("loppiskassan.base.dir", previousBase);
    }

    static V1SoldItem item(String id) {
        return new V1SoldItem("purchase-" + id, id, LocalDateTime.of(2026, 9, 7, 12, 0),
                42, 150, null, V1PaymentMethod.Kontant, false);
    }

    static V1CreateSoldItemsResponse reject(List<V1SoldItem> items) {
        V1CreateSoldItemsResponse response = new V1CreateSoldItemsResponse();
        for (V1SoldItem item : items) {
            V1RejectedItem rejection = new V1RejectedItem();
            rejection.setItem(SoldItemUtils.toApiSoldItem(item));
            rejection.setReason("Synthetic permanent rejection");
            response.addRejectedItemsItem(rejection);
        }
        return response;
    }

    @Test void failedRejectionWriteMustNotRemovePendingPurchase() throws Exception {
        PendingItemsStore pending = new PendingItemsStore(EVENT);
        pending.appendItems(List.of(item("one")));
        // A directory cannot be opened as the rejection file; no OS permission assumptions.
        Files.createDirectory(LocalEventPaths.getRejectedPurchasesPath(EVENT));
        BackgroundSyncManager sync = new BackgroundSyncManager((event, items) -> reject(items));

        assertThrows(IOException.class, () -> sync.syncOnce(EVENT));
        assertEquals(List.of("one"), pending.readPending().stream().map(V1SoldItem::getItemId).toList());
    }

    @Test void replayAfterRejectedWriteBeforePendingRewriteDoesNotDuplicateReviewItems() throws Exception {
        PendingItemsStore pending = new PendingItemsStore(EVENT);
        pending.appendItems(List.of(item("one")));
        // This is the on-disk state after a process stops between the two writes.
        se.goencoder.loppiskassan.utils.RejectedItemsHelper.saveRejectedItems(EVENT,
                reject(pending.readPending()).getRejectedItems());
        BackgroundSyncManager restarted = new BackgroundSyncManager((event, items) -> reject(items));

        restarted.syncOnce(EVENT);
        assertTrue(pending.readPending().isEmpty());
        assertEquals(1, new RejectedItemsStore(EVENT).readAll().size());
        assertEquals(150, new RejectedItemsStore(EVENT).readAll().getFirst().getPrice());
    }

    @Test void transientServerFailureLeavesPurchasePending() throws Exception {
        PendingItemsStore pending = new PendingItemsStore(EVENT);
        pending.appendItems(List.of(item("one")));
        for (int status : new int[]{408, 429, 500, 502, 503, 504}) {
            BackgroundSyncManager sync = new BackgroundSyncManager((event, items) -> {
                throw new se.goencoder.iloppis.invoker.ApiException(status, "Synthetic outage");
            });
            BackgroundSyncManager.SyncResult result = sync.syncOnce(EVENT);
            assertTrue(result.networkError(), "retryable status " + status);
            assertEquals(1, pending.readPending().size());
            assertTrue(new RejectedItemsStore(EVENT).readAll().isEmpty());
        }
    }

    @Test void lostResponseThenDuplicateRetryPreservesExactlyOneLocalSale() throws Exception {
        PendingItemsStore pending = new PendingItemsStore(EVENT);
        pending.appendItems(List.of(item("one")));
        BackgroundSyncManager first = new BackgroundSyncManager((event, items) -> {
            throw new se.goencoder.iloppis.invoker.ApiException(0, "Synthetic lost response");
        });
        assertTrue(first.syncOnce(EVENT).networkError());
        assertEquals(1, pending.readPending().size());
        BackgroundSyncManager restarted = new BackgroundSyncManager((event, items) -> {
            V1CreateSoldItemsResponse response = reject(items);
            response.getRejectedItems().forEach(r -> r.setErrorCode(
                    se.goencoder.iloppis.model.V1SoldItemErrorCode.SOLD_ITEM_ERROR_CODE_DUPLICATE_RECEIPT));
            return response;
        });
        assertEquals(1, restarted.syncOnce(EVENT).duplicates());
        assertTrue(pending.readPending().isEmpty());
        assertEquals(1, pending.readAll().size());
        assertEquals(150, pending.readAll().getFirst().getPrice());
        assertTrue(new RejectedItemsStore(EVENT).readAll().isEmpty());
    }

    @Test void permanentBadPurchaseDoesNotBlockHealthyPurchase() throws Exception {
        PendingItemsStore pending = new PendingItemsStore(EVENT);
        pending.appendItems(List.of(item("bad"), item("good")));
        BackgroundSyncManager sync = new BackgroundSyncManager((event, items) -> {
            if (items.getFirst().getItemId().equals("bad")) {
                throw new se.goencoder.iloppis.invoker.ApiException(400, "Synthetic invalid purchase");
            }
            V1CreateSoldItemsResponse response = new V1CreateSoldItemsResponse();
            items.forEach(i -> response.addAcceptedItemsItem(SoldItemUtils.toApiSoldItem(i)));
            return response;
        });
        BackgroundSyncManager.SyncResult result = sync.syncOnce(EVENT);
        assertEquals(1, result.accepted());
        assertEquals(1, result.rejected());
        assertTrue(pending.readPending().isEmpty());
        assertEquals("good", pending.readAll().getFirst().getItemId());
        assertEquals("bad", new RejectedItemsStore(EVENT).readAll().getFirst().getItemId());
    }

    @Test void malformedRejectionFileStopsCycleWithoutOverwritingEvidence() throws Exception {
        PendingItemsStore pending = new PendingItemsStore(EVENT);
        pending.appendItems(List.of(item("one")));
        Path path = LocalEventPaths.getRejectedPurchasesPath(EVENT);
        Files.writeString(path, "broken-json\n");
        BackgroundSyncManager sync = new BackgroundSyncManager((event, items) -> reject(items));
        assertThrows(IOException.class, () -> sync.syncOnce(EVENT));
        assertEquals("broken-json\n", Files.readString(path));
        assertEquals(1, pending.readPending().size());
    }

    @Test void checkoutPersistedDuringUploadIsNotLostByPendingRewrite() throws Exception {
        PendingItemsStore pending = new PendingItemsStore(EVENT);
        pending.appendItems(List.of(item("first")));
        BackgroundSyncManager sync = new BackgroundSyncManager((event, items) -> {
            // Another checkout appends while the upload runs outside pendingFileLock.
            try { pending.appendItems(List.of(item("next"))); }
            catch (IOException failure) { throw new AssertionError(failure); }
            V1CreateSoldItemsResponse response = new V1CreateSoldItemsResponse();
            items.forEach(i -> response.addAcceptedItemsItem(SoldItemUtils.toApiSoldItem(i)));
            return response;
        });
        sync.syncOnce(EVENT);
        assertEquals(List.of("next"), pending.readPending().stream().map(V1SoldItem::getItemId).toList());
        assertEquals(2, pending.readAll().size());
        assertEquals(300, pending.readAll().stream().mapToInt(V1SoldItem::getPrice).sum());
    }

    @Test void transientFailureInFirstPurchaseDoesNotBlockFollowingPurchases() throws Exception {
        for (int status : new int[]{0, 408, 429, 500, 502, 503, 504}) {
            String eventId = EVENT + "-" + status;
            PendingItemsStore pending = new PendingItemsStore(eventId);
            pending.appendItems(List.of(item("first"), item("second"), item("third")));
            List<String> attempts = new java.util.ArrayList<>();
            BackgroundSyncManager sync = new BackgroundSyncManager((event, items) -> {
                String id = items.getFirst().getItemId();
                attempts.add(id);
                if (attempts.size() == 1) {
                    throw new se.goencoder.iloppis.invoker.ApiException(status, "Synthetic transient failure");
                }
                V1CreateSoldItemsResponse response = new V1CreateSoldItemsResponse();
                items.forEach(i -> response.addAcceptedItemsItem(SoldItemUtils.toApiSoldItem(i)));
                return response;
            });

            BackgroundSyncManager.SyncResult result = sync.syncOnce(eventId);
            assertEquals(List.of("first", "second", "third"), attempts, "status " + status);
            assertTrue(result.networkError());
            assertEquals(2, result.accepted());
            assertEquals(0, result.rejected());
            assertEquals(List.of("first"), pending.readPending().stream().map(V1SoldItem::getItemId).toList());
            assertTrue(new RejectedItemsStore(eventId).readAll().isEmpty());

            assertEquals(1, sync.syncOnce(eventId).accepted());
            assertEquals(List.of("first", "second", "third", "first"), attempts);
            assertTrue(pending.readPending().isEmpty());
            assertEquals(3, pending.readAll().size());
            assertEquals(450, pending.readAll().stream().mapToInt(V1SoldItem::getPrice).sum());
        }
    }
}
