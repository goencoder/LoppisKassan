package se.goencoder.loppiskassan.controller;

import org.junit.jupiter.api.Test;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.storage.LocalEventRepository;
import se.goencoder.loppiskassan.ui.CashierPanelInterface;

import java.awt.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CashierTabControllerTest {
    static class StubView implements CashierPanelInterface {
        int change;
        @Override public void setFocusToSellerField() {}
        @Override public void enableCheckoutButtons(boolean enable) {}
        @Override public void addSoldItem(V1SoldItem item) {}
        @Override public void setPaidAmount(int amount) {}
        @Override public void setChange(int amount) { this.change = amount; }
        @Override public Map<Integer, Integer[]> getAndClearSellerPrices() { return Map.of(); }
        @Override public void clearView() {}
        @Override public void selected() {}
        @Override public Component getComponent() { return null; }
    }

    @Test
    void calculateChange() {
        try {
            Path tempDir = Files.createTempDirectory("loppiskassan-test");
            System.setProperty("user.home", tempDir.toString());
            ConfigurationStore.EVENT_ID_STR.set("local-test");
            LocalEventRepository.ensureEventStorage("local-test");
        } catch (Exception ignored) {
        }
        CashierTabController controller = (CashierTabController) CashierTabController.getInstance();
        StubView view = new StubView();
        controller.registerView(view);
        controller.cancelCheckout();
        controller.addItem(1, new Integer[]{100, 50});
        controller.calculateChange(200);
        assertEquals(50, view.change);
        controller.cancelCheckout();
    }
}
