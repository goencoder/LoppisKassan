package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.SoldItem;

public interface CashierPanelInterface extends SelectabableTab, UiComponent {
    void setFocusToSellerField();
    void enableCheckoutButtons(boolean enable);
    void addSoldItem(SoldItem item);
    void updateSumLabel(String newText);
    void updateNoItemsLabel(String newText);

    void updatePayedCashField(Integer amount);

    void updateChangeCashField(Integer amount);

    /**
     * Clears the seller and prices input fields.
     */
    void clearSellerAndPricesFields();
    void clearView();
}
