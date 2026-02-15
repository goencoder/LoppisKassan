package se.goencoder.loppiskassan.service;

import org.json.JSONObject;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.SoldItemsServiceCreateSoldItemsBody;
import se.goencoder.iloppis.model.V1CreateSoldItemsResponse;
import se.goencoder.iloppis.model.V1RejectedItem;
import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.config.ILoppisConfigurationStore;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.utils.RejectedItemsHelper;
import se.goencoder.loppiskassan.utils.SoldItemsResponseClassifier;
import se.goencoder.loppiskassan.utils.SoldItemUtils;
import se.goencoder.loppiskassan.utils.VendorRefreshHelper;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

/**
 * Cashier strategy for iLOPPIS mode (online market with central server).
 * 
 * - Seller approval checked against vendor registry
 * - Items uploaded to API
 * - Falls back to local save on network error (degraded mode)
 * - Auto-refreshes vendor list if seller not found initially (similar to Android app)
 */
public class IloppisCashierStrategy implements CashierStrategy {
    
    private static final Logger log = Logger.getLogger(IloppisCashierStrategy.class.getName());
    
    @Override
    public boolean validateSeller(int sellerId) {
        // First check: Use cached approved sellers list
        log.info(String.format("=== validateSeller(%d) START ===", sellerId));
        
        if (VendorRefreshHelper.isSellerApproved(sellerId)) {
            log.info(String.format("✓ Seller %d found in cache (approved)", sellerId));
            return true;
        }
        
        // Seller not found in cache - attempt auto-recovery by refreshing list
        // This handles the case where seller was approved AFTER cashier opened
        log.info(String.format("✗ Seller %d NOT in cached list - attempting auto-recovery", sellerId));
        
        String eventId = ILoppisConfigurationStore.getEventId();
        boolean refreshSuccess = VendorRefreshHelper.refreshApprovedSellers(eventId);
        log.info(String.format("Auto-recovery refresh result: %s", refreshSuccess ? "SUCCESS" : "FAILED"));
        
        if (refreshSuccess) {
            // Retry validation with fresh list
            boolean nowApproved = VendorRefreshHelper.isSellerApproved(sellerId);
            if (nowApproved) {
                log.info(String.format("✓ Auto-recovery SUCCESS: Seller %d now approved", sellerId));
                return true;
            } else {
                log.warning(String.format("✗ Auto-recovery FAILED: Seller %d still not in approved list", sellerId));
            }
        }
        
        // Seller still not approved after refresh
        log.warning(String.format("=== validateSeller(%d) END === RESULT: NOT APPROVED", sellerId));
        return false;
    }
    
    @Override
    public String getSellerValidationErrorKey() {
        return "cashier.seller_not_approved";
    }
    
    @Override
    public boolean persistItems(List<V1SoldItem> items, String purchaseId, V1PaymentMethod paymentMethod, LocalDateTime soldTime) throws Exception {
        String eventId = ILoppisConfigurationStore.getEventId();
        
        // Try to upload to API first
        try {
            log.info(() -> String.format("iLoppis: Uploading %d items to API (purchase=%s)", 
                items.size(), purchaseId));
            
            SoldItemsResponseClassifier.UploadOutcome outcome = uploadPurchaseWithRetry(eventId, items);

            log.info(() -> String.format("iLoppis: Upload successful - %d accepted, %d duplicates, %d rejected",
                outcome.acceptedItemIds().size(),
                outcome.duplicateItemIds().size(),
                outcome.rejectedItems().size()));

            List<V1SoldItem> uploadedItems = new java.util.ArrayList<>();
            for (V1SoldItem item : items) {
                if (item.getItemId() == null) {
                    continue;
                }
                if (outcome.uploadedItemIds().contains(item.getItemId())) {
                    item.setUploaded(true);
                    uploadedItems.add(item);
                }
            }

            if (!uploadedItems.isEmpty()) {
                Path pendingPath = LocalEventPaths.getPendingItemsPath(eventId);
                JsonlHelper.appendItems(pendingPath, uploadedItems);
                log.info("iLoppis: Items saved to local file for history tracking");
            }

            // Handle rejected items (validation errors, etc.)
            if (outcome.hasRejectedItems()) {
                int rejectedCount = outcome.rejectedItems().size();
                log.warning("Items rejected by server: " + rejectedCount);

                RejectedItemsHelper.saveRejectedItems(eventId, outcome.rejectedItems());
                Popup.WARNING.showAndWait(
                        LocalizationManager.tr("rejected.saved.title"),
                        LocalizationManager.tr("rejected.saved.message", rejectedCount));
            }

            BackgroundSyncManager.getInstance().notifyPendingCountChanged();
            RejectedItemsManager.getInstance().notifyRejectedCountChanged(eventId);
            
            return true;
            
        } catch (ApiException e) {
            // Network error: fall back to local save
            if (ApiHelper.isLikelyNetworkError(e)) {
                log.warning("Network error during upload - falling back to local save: " + e.getMessage());
                
                Path pendingPath = LocalEventPaths.getPendingItemsPath(eventId);
                JsonlHelper.appendItems(pendingPath, items);
                
                // Start background sync to retry upload automatically
                BackgroundSyncManager.getInstance().start(eventId);
                log.info("Background sync started for automatic retry");
                
                // Notify UI of pending count change
                BackgroundSyncManager.getInstance().notifyPendingCountChanged();
                
                return true; // Saved locally successfully
            } else {
                // Non-network error: propagate
                throw e;
            }
        }
    }

    private SoldItemsResponseClassifier.UploadOutcome uploadPurchaseWithRetry(String eventId, List<V1SoldItem> items) throws ApiException {
        V1CreateSoldItemsResponse response = uploadItemsToApi(eventId, items);
        SoldItemsResponseClassifier.UploadOutcome outcome = SoldItemsResponseClassifier.classify(response);

        List<V1RejectedItem> rejected = new ArrayList<>(outcome.rejectedItems());
        HashSet<String> accepted = new HashSet<>(outcome.acceptedItemIds());
        HashSet<String> duplicates = new HashSet<>(outcome.duplicateItemIds());

        boolean hasInvalidSeller = false;
        HashSet<String> collateralIds = new HashSet<>();
        for (V1RejectedItem rejectedItem : rejected) {
            if (SoldItemsResponseClassifier.isInvalidSeller(rejectedItem)) {
                hasInvalidSeller = true;
            }
            if (SoldItemsResponseClassifier.isCollateral(rejectedItem)) {
                String itemId = getRejectedItemId(rejectedItem);
                if (itemId != null && !itemId.isBlank()) {
                    collateralIds.add(itemId);
                }
            }
        }

        if (!hasInvalidSeller || collateralIds.isEmpty()) {
            return outcome;
        }

        List<V1SoldItem> retryItems = new ArrayList<>();
        for (V1SoldItem item : items) {
            if (item.getItemId() != null && collateralIds.contains(item.getItemId())) {
                retryItems.add(item);
            }
        }

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
        SoldItemsServiceCreateSoldItemsBody createSoldItems = new SoldItemsServiceCreateSoldItemsBody();
        for (V1SoldItem item : items) {
            se.goencoder.iloppis.model.V1SoldItem apiItem = SoldItemUtils.toApiSoldItem(item);
            if (item.getSoldTime() != null) {
                apiItem.setSoldTime(OffsetDateTime.of(item.getSoldTime(), OffsetDateTime.now().getOffset()));
            }
            createSoldItems.addItemsItem(apiItem);

            log.info(String.format("[API-REQ] Item: purchaseId=%s, itemId=%s, seller=%d, price=%d",
                apiItem.getPurchaseId(), apiItem.getItemId(), apiItem.getSeller(), apiItem.getPrice()));
        }

        return ApiHelper.INSTANCE
                .getSoldItemsServiceApi()
                .soldItemsServiceCreateSoldItems(eventId, createSoldItems);
    }

    private static String getRejectedItemId(V1RejectedItem rejectedItem) {
        if (rejectedItem == null || rejectedItem.getItem() == null) {
            return null;
        }
        return rejectedItem.getItem().getItemId();
    }
    
    @Override
    public String getModeDescription() {
        return "iloppis";
    }
}
