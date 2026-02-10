package se.goencoder.loppiskassan.service;

import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Strategy interface for cashier operations.
 * Implementations handle the differences between Local and iLoppis modes.
 * 
 * Template method pattern skeleton:
 * 1. Validate seller (different: local=always true, iLoppis=check approval)
 * 2. Add item to cart (same: just add to list)
 * 3. Save/upload items (different: local=file, iLoppis=API)
 */
public interface CashierStrategy {
    
    /**
     * Validates if a seller can be used in this mode.
     * Local mode: always returns true (no approval needed)
     * iLoppis mode: checks against approved vendor list
     * 
     * @param sellerId the seller number to validate
     * @return true if seller can be used
     */
    boolean validateSeller(int sellerId);
    
    /**
     * Gets a user-friendly error message when seller validation fails.
     * Only called if validateSeller() returns false.
     * 
     * @return localization key for error message
     */
    String getSellerValidationErrorKey();
    
    /**
     * Persists sold items after checkout.
     * Local mode: saves to local JSONL file
     * iLoppis mode: uploads to API, falls back to local on network error
     * 
     * @param items the items to persist
     * @param purchaseId the purchase ID grouping these items
     * @param paymentMethod the payment method used
     * @param soldTime the timestamp of the sale
     * @return true if persist succeeded, false if failed
     * @throws Exception if a non-recoverable error occurs
     */
    boolean persistItems(List<V1SoldItem> items, String purchaseId, V1PaymentMethod paymentMethod, LocalDateTime soldTime) throws Exception;
    
    /**
     * Returns a description of this cashier mode for logging.
     */
    String getModeDescription();
}
