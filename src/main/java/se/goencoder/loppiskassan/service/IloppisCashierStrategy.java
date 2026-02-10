package se.goencoder.loppiskassan.service;

import org.json.JSONObject;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.SoldItemsServiceCreateSoldItemsBody;
import se.goencoder.iloppis.model.V1CreateSoldItemsResponse;
import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.config.ILoppisConfigurationStore;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
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
            
            // Handle rejected items (duplicate receipts, etc.)
            if (response.getRejectedItems() != null && !response.getRejectedItems().isEmpty()) {
                // TODO: Handle rejected items - save to rejection log?
                log.warning("Some items were rejected by server: " + response.getRejectedItems().size());
            }
            
            return true;
            
        } catch (ApiException e) {
            // Network error: fall back to local save
            if (ApiHelper.isLikelyNetworkError(e)) {
                log.warning("Network error during upload - falling back to local save: " + e.getMessage());
                
                Path pendingPath = LocalEventPaths.getPendingItemsPath(eventId);
                JsonlHelper.appendItems(pendingPath, items);
                
                // TODO: Trigger background sync attempt
                
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
