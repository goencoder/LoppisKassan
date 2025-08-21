package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.SoldItem;

import java.util.Map;

/**
 * View contract for the Cashier screen.
 * <p>
 * Mode awareness:
 * <ul>
 *   <li><b>Online:</b> UI may show network-related states (seller approval fetched, Swish enabled).</li>
 *   <li><b>Offline:</b> UI should guide the user to cash-only flow and surface limited validation.</li>
 * </ul>
 */
public interface CashierPanelInterface extends SelectabableTab, UiComponent {

    /**
     * Move focus to the seller input field.
     * Use when the view is shown or after clearing state.
     */
    void setFocusToSellerField();

    /**
     * Enable/disable checkout action buttons (cash/Swish) based on current state
     * such as items present, seller approval, or mode limitations.
     *
     * @param enable {@code true} to enable, {@code false} to disable
     */
    void enableCheckoutButtons(boolean enable);

    /**
     * Append a sold item to the running list in the UI.
     * Implementations should update totals and relevant counters.
     *
     * @param item the item that was sold
     */
    void addSoldItem(SoldItem item);

    /**
     * Update the entered cash amount used for change calculation.
     *
     * @param amount amount in whole currency units (e.g., SEK)
     */
    void setPaidAmount(int amount);

    /**
     * Update the calculated change-to-give field.
     *
     * @param amount change amount in whole currency units
     */
    void setChange(int amount);

    /**
     * Retrieve and clear the seller→prices mapping from the UI in a single, atomic operation.
     * Implementations should also reset the input fields to prepare for the next transaction.
     *
     * @return map keyed by seller id; each value is a two-element array
     *         {@code [priceForVendor, priceForMarketOwner]}
     */
    Map<Integer, Integer[]> getAndClearSellerPrices();

    /**
     * Clear all input fields, tables, and derived labels.
     * Typically used after a successful checkout or when switching events/mode.
     */
    void clearView();
}
