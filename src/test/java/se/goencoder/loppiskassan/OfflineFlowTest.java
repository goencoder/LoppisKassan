package se.goencoder.loppiskassan;

import org.junit.jupiter.api.Test;
import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.controller.CashierTabController;
import se.goencoder.loppiskassan.controller.HistoryTabController;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.records.FileHelper;
import se.goencoder.loppiskassan.records.FormatHelper;
import se.goencoder.loppiskassan.PaymentMethod;
import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.ui.CashierPanelInterface;
import se.goencoder.loppiskassan.ui.HistoryPanelInterface;

import java.awt.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static se.goencoder.loppiskassan.ui.Constants.BUTTON_PAY_OUT;

public class OfflineFlowTest {

    static class DummyCashierPanel implements CashierPanelInterface {
        @Override public void setFocusToSellerField() {}
        @Override public void enableCheckoutButtons(boolean enable) {}
        @Override public void addSoldItem(SoldItem item) {}
        @Override public void setPaidAmount(int amount) {}
        @Override public void setChange(int amount) {}
        @Override public Map<Integer, Integer[]> getAndClearSellerPrices() { return Map.of(); }
        @Override public void clearView() {}
        @Override public void selected() {}
        @Override public Component getComponent() { return null; }
    }

    static class DummyHistoryPanel implements HistoryPanelInterface {
        String sellerFilter;
        String paymentMethodFilter;
        String paidFilter;
        @Override public void updateHistoryTable(List<SoldItem> items) {}
        @Override public void updateSumLabel(String sum) {}
        @Override public void updateNoItemsLabel(String noItems) {}
        @Override public String getSellerFilter() { return sellerFilter; }
        @Override public String getPaymentMethodFilter() { return paymentMethodFilter; }
        @Override public String getPaidFilter() { return paidFilter; }
        @Override public void updateSellerDropdown(Set<String> sellers) {}
        @Override public void enableButton(String buttonName, boolean enable) {}
        @Override public void setImportButtonText(String text) {}
        @Override public void selected() {}
        @Override public Component getComponent() { return null; }
    }

    @Test
    void offlineCheckoutAndPayoutFlow() throws Exception {
        Path tempDir = Files.createTempDirectory("loppiskassan-test");
        System.setProperty("user.dir", tempDir.toString());

        FileHelper.createDirectories();
        ConfigurationStore.OFFLINE_EVENT_BOOL.setBooleanValue(true);
        LocalizationManager.initialize();

        DummyCashierPanel cashierView = new DummyCashierPanel();
        CashierTabController cashier = (CashierTabController) CashierTabController.getInstance();
        cashier.registerView(cashierView);

        for (int i = 0; i < 100; i++) {
            int sellerId = (i % 10) + 1;
            int price = 10 + i;
            cashier.addItem(sellerId, new Integer[]{price});
            PaymentMethod pm = sellerId == 1 ? PaymentMethod.Swish : PaymentMethod.Kontant;
            cashier.checkout(pm);
        }

        String csv = FileHelper.readFromFile(FileHelper.LOPPISKASSAN_CSV);
        List<SoldItem> items = FormatHelper.toItems(csv, true);
        assertEquals(100, items.size());

        HistoryTabController history = HistoryTabController.getInstance();
        DummyHistoryPanel historyView = new DummyHistoryPanel();
        history.registerView(historyView);
        history.loadHistory();

        historyView.sellerFilter = "1";
        historyView.paymentMethodFilter = PaymentMethod.Swish.name();
        historyView.paidFilter = "false";
        history.buttonAction(BUTTON_PAY_OUT);

        historyView.sellerFilter = "2";
        historyView.paymentMethodFilter = null;
        historyView.paidFilter = "false";
        history.buttonAction(BUTTON_PAY_OUT);

        String csvAfter = FileHelper.readFromFile(FileHelper.LOPPISKASSAN_CSV);
        List<SoldItem> updated = FormatHelper.toItems(csvAfter, true);

        long seller1SwishPaid = updated.stream()
                .filter(i -> i.getSeller() == 1 && i.getPaymentMethod() == PaymentMethod.Swish && i.isCollectedBySeller())
                .count();
        long seller1SwishTotal = updated.stream()
                .filter(i -> i.getSeller() == 1 && i.getPaymentMethod() == PaymentMethod.Swish)
                .count();
        assertEquals(seller1SwishTotal, seller1SwishPaid);

        long seller2Paid = updated.stream().filter(i -> i.getSeller() == 2 && i.isCollectedBySeller()).count();
        long seller2Total = updated.stream().filter(i -> i.getSeller() == 2).count();
        assertEquals(seller2Total, seller2Paid);

        long otherPaid = updated.stream()
                .filter(i -> i.getSeller() != 1 && i.getSeller() != 2 && i.isCollectedBySeller())
                .count();
        assertEquals(0, otherPaid);
    }
}
