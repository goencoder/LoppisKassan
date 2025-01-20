package se.goencoder.loppiskassan.controller;


import se.goencoder.loppiskassan.ui.CashierPanelInterface;

import javax.swing.*;

public interface CashierControllerInterface {
    void setupCheckoutCashButtonAction(JButton checkoutCashButton);
    void setupCheckoutSwishButtonAction(JButton checkoutSwishButton);
    void setupCancelCheckoutButtonAction(JButton cancelCheckoutButton);

    void setupPricesTextFieldAction(JTextField pricesTextField);

    void registerView(CashierPanelInterface view);

    void deleteItem(String itemId);

    void calculateChange(int payedAmount);

    boolean isSellerApproved(int sellerId);
}
