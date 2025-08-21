package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.SoldItem;

import java.util.List;
import java.util.Set;

/**
 * View contract for the Sales History screen.
 * <p>
 * Mode awareness:
 * <ul>
 *   <li><b>Online:</b> history may include server-side entries and support import/refresh.</li>
 *   <li><b>Offline:</b> history is limited to locally captured sales during the session or local storage.</li>
 * </ul>
 */
public interface HistoryPanelInterface extends SelectabableTab, UiComponent {

    /**
     * Replace the table contents with the supplied sales items.
     * Implementations should preserve current filters if applicable.
     *
     * @param items sales items to display
     */
    void updateHistoryTable(List<SoldItem> items);

    /**
     * Update the formatted sum display shown in the history view.
     *
     * @param sum localized/ formatted total text (e.g., "1 234 kr")
     */
    void updateSumLabel(String sum);

    /**
     * Update the text reflecting the number of items.
     *
     * @param noItems localized text (e.g., "0 varor")
     */
    void updateNoItemsLabel(String noItems);

    /**
     * @return current seller filter value (empty if no filter)
     */
    String getSellerFilter();

    /**
     * @return current payment method filter (e.g., "CASH", "SWISH", or empty)
     */
    String getPaymentMethodFilter();

    /**
     * @return current paid-state filter (e.g., "PAID", "UNPAID", or empty)
     */
    String getPaidFilter();

    /**
     * Replace the seller dropdown contents with the provided set.
     *
     * @param sellers distinct seller labels to show
     */
    void updateSellerDropdown(Set<String> sellers);

    /**
     * Enable/disable a named button in the view (e.g., "import", "export").
     *
     * @param buttonName logical name of the button
     * @param enable {@code true} to enable, {@code false} to disable
     */
    void enableButton(String buttonName, boolean enable);

    /**
     * Update the import button caption to reflect the current action/ mode
     * (e.g., "Importera" vs "Synka" when online).
     *
     * @param text localized button text
     */
    void setImportButtonText(String text);
}
