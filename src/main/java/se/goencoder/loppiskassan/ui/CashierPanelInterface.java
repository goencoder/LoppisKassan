package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.SoldItem;

import java.util.Map;

public interface CashierPanelInterface extends SelectabableTab, UiComponent{
    void setFocusToSellerField();
    void enableCheckoutButtons(boolean enable);
    void addSoldItem(SoldItem item);
    void updateSumLabel(String newText);
    void updateNoItemsLabel(String newText);

    void updatePayedCashField(Integer amount);

    void updateChangeCashField(Integer amount);

    /**
     * Hämtar och rensar säljare och priser från vyn.
     * @return Map med säljare och priser.
     */
    Map<Integer, Integer[]> getAndClearSellerPrices();
    void clearView();
}
