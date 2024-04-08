package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.SoldItem;

import java.util.List;
import java.util.Set;

public interface HistoryPanelInterface extends SelectabableTab{
    void updateHistoryTable(List<SoldItem> items);
    void updateSumLabel(String sum);
    void updateNoItemsLabel(String noItems);
    String getSellerFilter();
    String getPaymentMethodFilter();
    void clearView();

    String getPaidFilter();
    void updateSellerDropdown(Set<String> sellers);
    void enableButton(String buttonName, boolean enable);


}
