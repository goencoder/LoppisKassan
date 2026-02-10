package se.goencoder.loppiskassan.controller;

import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.ui.CashierPanelInterface;

/**
 * Controller contract for cashier interactions.
 * <p>
 * Mode behavior:
 * <ul>
 *   <li><b>Online:</b> may validate sellers against the server and offer Swish checkout.</li>
 *   <li><b>Local:</b> should skip remote validations and disable Swish, allowing cash-only flow.</li>
 * </ul>
 */
public interface CashierControllerInterface {

    /**
     * Register the view so the controller can push updates and read user inputs.
     * @param view cashier panel view
     */
    void registerView(CashierPanelInterface view);

    /**
     * Handle user submitting prices for a given seller.
     * Called when user presses Enter in the prices field.
     */
    void onPricesSubmitted();

    /**
     * Handle checkout request with the specified payment method.
     * @param paymentMethod the payment method (cash or Swish)
     */
    void onCheckout(V1PaymentMethod paymentMethod);

    /**
     * Handle cancel checkout request.
     * Clears the current transaction and resets the UI.
     */
    void onCancelCheckout();

    /**
     * Delete a previously added item by its identifier.
     * Mode-independent, but errors/ confirmations may differ by mode.
     *
     * @param itemId unique identifier of the item to remove
     */
    void deleteItem(String itemId);

    /**
     * Compute change to give for a cash payment based on current total.
     * @param payedAmount amount tendered by the customer (whole units)
     */
    void calculateChange(int payedAmount);

    /**
     * Determine whether a seller is approved to sell.
     * <p>
    /**
     * Validates if a seller can be used in the current mode (local/iLoppis).
     * 
     * <b>Local mode:</b> always returns true (no approval needed)
     * <b>iLoppis mode:</b> checks against approved vendor list
     *
     * @param sellerId numeric seller identifier
     * @return {@code true} if approved, otherwise {@code false}
     */
    boolean validateSeller(int sellerId);
    
    /**
     * Gets the localization key for the error message when seller validation fails.
     * Only relevant for iLoppis mode where seller approval is required.
     * 
     * @return localization key for the error message
     */
    String getSellerValidationErrorKey();
}
