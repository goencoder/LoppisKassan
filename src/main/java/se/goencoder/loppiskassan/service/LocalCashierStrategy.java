package se.goencoder.loppiskassan.service;

import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.config.LocalConfigurationStore;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * Cashier strategy for LOCAL mode (offline flea market).
 * 
 * - No seller approval needed (all sellers accepted)
 * - Items saved to local JSONL file only
 * - No API calls
 */
public class LocalCashierStrategy implements CashierStrategy {
    
    private static final Logger log = Logger.getLogger(LocalCashierStrategy.class.getName());
    
    @Override
    public boolean validateSeller(int sellerId) {
        // Local mode: all sellers are valid (no approval registry)
        return true;
    }
    
    @Override
    public String getSellerValidationErrorKey() {
        // Should never be called since validateSeller() always returns true
        throw new UnsupportedOperationException("Local mode does not validate sellers");
    }
    
    @Override
    public boolean persistItems(List<V1SoldItem> items, String purchaseId, V1PaymentMethod paymentMethod, LocalDateTime soldTime) throws Exception {
        String eventId = LocalConfigurationStore.getEventId();
        Path pendingPath = LocalEventPaths.getPendingItemsPath(eventId);
        
        log.info(() -> String.format("Local: Saving %d items to %s (purchase=%s)", 
            items.size(), pendingPath.getFileName(), purchaseId));
        
        // Save to local JSONL file
        JsonlHelper.appendItems(pendingPath, items);
        
        return true;
    }
    
    @Override
    public String getModeDescription() {
        return "local";
    }
}
