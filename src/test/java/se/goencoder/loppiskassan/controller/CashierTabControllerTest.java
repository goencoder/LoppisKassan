package se.goencoder.loppiskassan.controller;

import org.junit.jupiter.api.Test;
import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.ui.CashierPanelInterface;

import java.awt.Component;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CashierTabControllerTest {
    static class StubView implements CashierPanelInterface {
        int change;
        @Override public void setFocusToSellerField() {}
        @Override public void enableCheckoutButtons(boolean enable) {}
        @Override public void addSoldItem(SoldItem item) {}
        @Override public void setPaidAmount(int amount) {}
        @Override public void setChange(int amount) { this.change = amount; }
        @Override public Map<Integer, Integer[]> getAndClearSellerPrices() { return Map.of(); }
        @Override public void clearView() {}
        @Override public void selected() {}
        @Override public Component getComponent() { return null; }
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
}
