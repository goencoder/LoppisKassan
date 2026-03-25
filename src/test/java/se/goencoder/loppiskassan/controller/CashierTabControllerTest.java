package se.goencoder.loppiskassan.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.config.AppMode;
import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.config.GlobalConfigurationStore;
import se.goencoder.loppiskassan.config.LocalConfigurationStore;
import se.goencoder.loppiskassan.storage.LocalEventRepository;
import se.goencoder.loppiskassan.service.CashierHeartbeatService;
import se.goencoder.loppiskassan.ui.CashierPanelInterface;

import java.awt.Component;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CashierTabControllerTest {
    @TempDir
    Path tempDir;

    private String previousUserHome;

    static class StubView implements CashierPanelInterface {
        int change;
        @Override public void setFocusToSellerField() {}
        @Override public void enableCheckoutButtons(boolean enable) {}
        @Override public void addSoldItem(V1SoldItem item) {}
        @Override public void setPaidAmount(int amount) {}
        @Override public void setChange(int amount) { this.change = amount; }
        @Override public Map<Integer, Integer[]> getAndClearSellerPrices() { return Map.of(); }
        @Override public void clearView() {}
        @Override public void showCheckoutSuccess(se.goencoder.loppiskassan.V1PaymentMethod paymentMethod, int totalAmount) {}
        @Override public void selected() {}
        @Override public Component getComponent() { return null; }
    }

    @BeforeEach
    void setUp() throws Exception {
        previousUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toString());
        GlobalConfigurationStore.reset();
        AppModeManager.setMode(AppMode.LOCAL);
        LocalConfigurationStore.reset();
        LocalConfigurationStore.setEventId("local-test");
        LocalEventRepository.ensureEventStorage("local-test");
    }

    @AfterEach
    void tearDown() {
        GlobalConfigurationStore.reset();
        LocalConfigurationStore.reset();
        if (previousUserHome != null) {
            System.setProperty("user.home", previousUserHome);
        }
    }

    @Test
    void calculateChange() {
        CashierTabController controller = (CashierTabController) CashierTabController.getInstance();
        StubView view = new StubView();
        controller.registerView(view);
        controller.cancelCheckout();
        controller.addItem(1, new Integer[]{100, 50});
        controller.calculateChange(200);
        assertEquals(50, view.change);
        controller.cancelCheckout();
    }

    @Test
    void refreshHeartbeatDisplayNameFromConfigUsesPersistedAlias() {
        CashierTabController controller = (CashierTabController) CashierTabController.getInstance();
        String previousName = GlobalConfigurationStore.getCashierName();

        try {
            GlobalConfigurationStore.setCashierName("Kassa Entré");
            controller.refreshHeartbeatDisplayNameFromConfig();

            assertEquals("Kassa Entré", controller.getHeartbeatDisplayName());
        } finally {
            GlobalConfigurationStore.setCashierName(previousName);
            controller.refreshHeartbeatDisplayNameFromConfig();
        }
    }

    @Test
    void applyHeartbeatResultPersistsServerAlias() {
        CashierTabController controller = (CashierTabController) CashierTabController.getInstance();
        String previousName = GlobalConfigurationStore.getCashierName();

        try {
            GlobalConfigurationStore.setCashierName("Gammalt namn");

            controller.applyHeartbeatResult(new CashierHeartbeatService.HeartbeatResult("Server Alias"));

            assertEquals("Server Alias", controller.getHeartbeatDisplayName());
            assertEquals("Server Alias", GlobalConfigurationStore.getCashierName());
        } finally {
            GlobalConfigurationStore.setCashierName(previousName);
            controller.refreshHeartbeatDisplayNameFromConfig();
        }
    }
}
