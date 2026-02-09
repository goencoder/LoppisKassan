package se.goencoder.loppiskassan;

import org.junit.jupiter.api.Test;
import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.controller.CashierTabController;
import se.goencoder.loppiskassan.controller.HistoryTabController;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.storage.LocalEventRepository;
import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;
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
        @Override public void addSoldItem(V1SoldItem item) {}
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
        @Override public void updateHistoryTable(List<V1SoldItem> items) {}
        @Override public void updateSumLabel(String sum) {}
        @Override public void updateNoItemsLabel(String noItems) {}
        @Override public String getSellerFilter() { return sellerFilter; }
        @Override public String getPaymentMethodFilter() { return paymentMethodFilter; }
        @Override public String getPaidFilter() { return paidFilter; }
        @Override public void updateSellerDropdown(Set<String> sellers) {}
        @Override public void enableButton(String buttonName, boolean enable) {}
        boolean importButtonVisible = true;
        @Override public void setImportButtonText(String text) {}
        @Override public void setImportButtonVisible(boolean visible) { importButtonVisible = visible; }
        @Override public boolean isImportButtonVisible() { return importButtonVisible; }
        @Override public void selected() {}
        @Override public Component getComponent() { return null; }
        @Override public java.io.File[] selectFilesForImport(java.io.File initialDir) { return null; }
        @Override public void copyToClipboard(String text) {}
    }

    @Test
    void localCheckoutAndPayoutFlow() throws Exception {
        Path tempDir = Files.createTempDirectory("loppiskassan-test");
        System.setProperty("user.home", tempDir.toString());

        LocalEventRepository.ensureEventStorage("local-test");
        ConfigurationStore.LOCAL_EVENT_BOOL.setBooleanValue(true);
        ConfigurationStore.EVENT_ID_STR.set("local-test");
        LocalizationManager.initialize();

        DummyCashierPanel cashierView = new DummyCashierPanel();
        CashierTabController cashier = (CashierTabController) CashierTabController.getInstance();
        cashier.registerView(cashierView);

        for (int i = 0; i < 100; i++) {
            int sellerId = (i % 10) + 1;
            int price = 10 + i;
            cashier.addItem(sellerId, new Integer[]{price});
            V1PaymentMethod pm = sellerId == 1 ? V1PaymentMethod.Swish : V1PaymentMethod.Kontant;
            cashier.checkout(pm);
        }

        List<V1SoldItem> items = JsonlHelper.readItems(LocalEventPaths.getPendingItemsPath("local-test"));
        assertEquals(100, items.size());

        HistoryTabController history = HistoryTabController.getInstance();
        DummyHistoryPanel historyView = new DummyHistoryPanel();
        history.registerView(historyView);
        history.loadHistory();

        historyView.sellerFilter = "1";
        historyView.paymentMethodFilter = V1PaymentMethod.Swish.name();
        historyView.paidFilter = "false";
        history.buttonAction(BUTTON_PAY_OUT);

        historyView.sellerFilter = "2";
        historyView.paymentMethodFilter = null;
        historyView.paidFilter = "false";
        history.buttonAction(BUTTON_PAY_OUT);

        List<V1SoldItem> updated = JsonlHelper.readItems(LocalEventPaths.getPendingItemsPath("local-test"));

        long seller1SwishPaid = updated.stream()
                .filter(i -> i.getSeller() == 1 && i.getPaymentMethod() == V1PaymentMethod.Swish && i.isCollectedBySeller())
                .count();
        long seller1SwishTotal = updated.stream()
                .filter(i -> i.getSeller() == 1 && i.getPaymentMethod() == V1PaymentMethod.Swish)
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

    @Test
    void importButtonIsHiddenForLocalEvents() throws Exception {
        Path tempDir = Files.createTempDirectory("loppiskassan-test-btn");
        System.setProperty("user.home", tempDir.toString());

        LocalEventRepository.ensureEventStorage("local-test");
        ConfigurationStore.LOCAL_EVENT_BOOL.setBooleanValue(true);
        ConfigurationStore.EVENT_ID_STR.set("local-test");
        LocalizationManager.initialize();

        DummyHistoryPanel historyView = new DummyHistoryPanel();
        // Button starts visible by default
        assertEquals(true, historyView.isImportButtonVisible(),
                "Import button should start visible before controller initializes");

        HistoryTabController history = HistoryTabController.getInstance();
        history.registerView(historyView);
        history.loadHistory();

        // After controller init in local mode, button must be hidden
        assertEquals(false, historyView.isImportButtonVisible(),
                "Import/update button must be hidden for local events");

        // Also verify it stays hidden after filter changes
        historyView.sellerFilter = "1";
        history.filterUpdated();
        assertEquals(false, historyView.isImportButtonVisible(),
                "Import/update button must stay hidden after filter change in local mode");
    }
}
