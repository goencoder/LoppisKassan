package se.goencoder.loppiskassan.service;

import org.json.JSONObject;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.SoldItemsServiceCreateSoldItemsBody;
import se.goencoder.iloppis.model.V1CreateSoldItemsResponse;
import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.config.ILoppisConfigurationStore;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.utils.RejectedItemsHelper;
import se.goencoder.loppiskassan.utils.SoldItemUtils;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * Cashier strategy for iLOPPIS mode (online market with central server).
 * 
 * - Seller approval checked against vendor registry
 * - Items uploaded to API
 * - Falls back to local save on network error (degraded mode)
 */
public class IloppisCashierStrategy implements CashierStrategy {
    
    private static final Logger log = Logger.getLogger(IloppisCashierStrategy.class.getName());
    
    @Override
    public boolean validateSeller(int sellerId) {
        // Check if seller is in the approved vendors list
        String approvedSellersJson = ILoppisConfigurationStore.getApprovedSellers();
        if (approvedSellersJson == null || approvedSellersJson.isBlank()) {
            log.warning("No approved sellers list available - rejecting seller " + sellerId);
            return false;
        }
        
        try {
            return new JSONObject(approvedSellersJson)
                    .getJSONArray("approvedSellers")
                    .toList()
                    .contains(sellerId);
        } catch (Exception e) {
            log.warning("Failed to parse approved sellers JSON: " + e.getMessage());
            return false;
        }
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
            SoldItemsServiceCreateSoldItemsBody createSoldItems = new SoldItemsServiceCreateSoldItemsBody();
            for (V1SoldItem item : items) {
                se.goencoder.iloppis.model.V1SoldItem apiItem = SoldItemUtils.toApiSoldItem(item);
                createSoldItems.addItemsItem(apiItem);
            }
            
            log.info(() -> String.format("iLoppis: Uploading %d items to API (purchase=%s)", 
                items.size(), purchaseId));
            
            V1CreateSoldItemsResponse response = ApiHelper.INSTANCE
                    .getSoldItemsServiceApi()
                    .soldItemsServiceCreateSoldItems(eventId, createSoldItems);
            
            log.info(() -> String.format("iLoppis: Upload successful - %d accepted, %d rejected",
                response.getAcceptedItems().size(), 
                response.getRejectedItems() != null ? response.getRejectedItems().size() : 0));
            
            // Mark items as uploaded and save to local file for history view
            for (V1SoldItem item : items) {
                item.setUploaded(true);
            }
            Path pendingPath = LocalEventPaths.getPendingItemsPath(eventId);
            JsonlHelper.appendItems(pendingPath, items);
            log.info("iLoppis: Items saved to local file for history tracking");
            
            // Notify UI of pending count change (items are uploaded, count should be 0)
            BackgroundSyncManager.getInstance().notifyPendingCountChanged();
            
            // Handle rejected items (duplicate receipts, validation errors, etc.)
            if (response.getRejectedItems() != null && !response.getRejectedItems().isEmpty()) {
                int rejectedCount = response.getRejectedItems().size();
                log.warning("Items rejected by server: " + rejectedCount);
                
                // Save rejected items to log file
                RejectedItemsHelper.saveRejectedItems(eventId, response.getRejectedItems());
                
                // Show warning popup to cashier
                String message = String.format(
                    LocalizationManager.tr("error.upload_rejected") + "\n\n" +
                    RejectedItemsHelper.buildRejectionMessage(response.getRejectedItems())
                );
                Popup.warn(message);
            }
            
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
    
    @Override
    public String getModeDescription() {
        return "iloppis";
    }
}
