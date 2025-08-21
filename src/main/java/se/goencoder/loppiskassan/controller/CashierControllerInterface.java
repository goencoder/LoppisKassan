package se.goencoder.loppiskassan.controller;

import se.goencoder.loppiskassan.ui.CashierPanelInterface;

import javax.swing.*;

/**
 * Controller contract for cashier interactions.
 * <p>
 * Mode behavior:
 * <ul>
 *   <li><b>Online:</b> may validate sellers against the server and offer Swish checkout.</li>
 *   <li><b>Offline:</b> should skip remote validations and disable Swish, allowing cash-only flow.</li>
 * </ul>
 */
public interface CashierControllerInterface {

    /**
     * Wire the cash checkout action to the provided button.
     * @param checkoutCashButton Swing button to receive the action
     */
    void setupCheckoutCashButtonAction(JButton checkoutCashButton);

    /**
     * Wire the Swish checkout action to the provided button.
     * Implementations should disable or no-op this action in offline mode.
     *
     * @param checkoutSwishButton Swing button to receive the action
     */
    void setupCheckoutSwishButtonAction(JButton checkoutSwishButton);

    /**
     * Wire the cancel checkout action to the provided button.
     * @param cancelCheckoutButton Swing button to receive the action
     */
    void setupCancelCheckoutButtonAction(JButton cancelCheckoutButton);

    /**
     * Attach input handling to the prices text field (e.g., Enter to add item, validation).
     * @param pricesTextField the text field with price input
     */
    void setupPricesTextFieldAction(JTextField pricesTextField);

    /**
     * Register the view so the controller can push updates and read user inputs.
     * @param view cashier panel view
     */
    void registerView(CashierPanelInterface view);

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
     * <b>Online:</b> typically verified via server lookup.
     * <b>Offline:</b> rely on locally cached state or default to permissive rules.
     *
     * @param sellerId numeric seller identifier
     * @return {@code true} if approved, otherwise {@code false}
     */
    boolean isSellerApproved(int sellerId);
}
