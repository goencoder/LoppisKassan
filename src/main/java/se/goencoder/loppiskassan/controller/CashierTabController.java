package se.goencoder.loppiskassan.controller;

import se.goencoder.loppiskassan.PaymentMethod;
import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.records.FileHelper;
import se.goencoder.loppiskassan.records.FormatHelper;
import se.goencoder.loppiskassan.ui.CashierPanelInterface;
import se.goencoder.loppiskassan.ui.Popup;
import javax.swing.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controls the cashier tab, handling the sales process, including item management and checkout procedures.
 */
public class CashierTabController implements CashierControllerInterface {
    private static final CashierTabController instance = new CashierTabController();
    private final List<SoldItem> items = new ArrayList<>();
    private CashierPanelInterface view;

    // Private constructor to enforce singleton pattern.
    private CashierTabController() {}

    // Returns the singleton instance of this controller.
    public static CashierControllerInterface getInstance() {
        return instance;
    }

    // Sets up the button for cash checkout.
    public void setupCheckoutCashButtonAction(JButton checkoutCashButton) {
        checkoutCashButton.addActionListener(e -> checkout(PaymentMethod.Kontant));
    }

    // Sets up the button for Swish (digital) checkout.
    public void setupCheckoutSwishButtonAction(JButton checkoutSwishButton) {
        checkoutSwishButton.addActionListener(e -> checkout(PaymentMethod.Swish));
    }

    // Sets up the button to cancel the checkout process.
    public void setupCancelCheckoutButtonAction(JButton cancelCheckoutButton) {
        cancelCheckoutButton.addActionListener(e -> cancelCheckout());
    }

    // Configures the action for the prices text field.
    @Override
    public void setupPricesTextFieldAction(JTextField pricesTextField) {
        pricesTextField.addActionListener(e -> {
            Map<Integer,Integer[]> prices = view.getAndClearSellerPrices();
            prices.forEach(this::addItem);
            view.setFocusToSellerField();
        });
    }

    // Registers the interface of the cashier panel view with this controller.
    public void registerView(CashierPanelInterface view) {
        this.view = view;
    }

    // Removes a sold item by its ID and recalculates the total.
    @Override
    public void deleteItem(String itemId) {
        items.removeIf(item -> item.getItemId().equals(itemId));
        reCalculate();
    }

    @Override
    public void calculateChange(int payedAmount) {
        int totalSum = getSum();
        int change = payedAmount - totalSum;
        view.updateChangeCashField(change);
    }

    // Adds an item to the list and recalculates the total.
    public void addItem(Integer sellerId, Integer[] prices) {
        FileHelper.assertRecordFileRights();
        for (Integer price : prices) {
            SoldItem soldItem = new SoldItem(sellerId, price, null);
            items.add(soldItem);
            view.addSoldItem(soldItem);
        }
        reCalculate();
    }

    // Handles the checkout process for different payment methods.
    public void checkout(PaymentMethod paymentMethod) {
        LocalDateTime now = LocalDateTime.now();
        String purchaseId = UUID.randomUUID().toString();
        items.forEach(item -> {
            item.setSoldTime(now);
            item.setPaymentMethod(paymentMethod);
            item.setPurchaseId(purchaseId);
        });
        try {
            FileHelper.saveToFile(FormatHelper.toCVS(items));
            items.clear();
            view.clearView();
        } catch (IOException e) {
            Popup.ERROR.showAndWait("Could not save to file (" + FileHelper.getLogFilePath() + ")", e.getMessage());
        }
    }

    // Cancels the checkout, clearing the items and view.
    public void cancelCheckout() {
        items.clear();
        view.clearView();
    }

    // Recalculates and updates the total sum and item count displayed.
    private void reCalculate() {
        int totalSum = getSum();
        int roundedSum = (totalSum + 99) / 100 * 100; // Round up to the nearest 100.
        int change = roundedSum - totalSum;

        view.clearView();
        items.forEach(view::addSoldItem);
        view.updateSumLabel(String.valueOf(totalSum));
        view.updateNoItemsLabel(String.valueOf(items.size()));
        view.updateChangeCashField(change);
        view.updatePayedCashField(roundedSum);
        view.setFocusToSellerField();
        view.enableCheckoutButtons(items.size() > 0);
    }

    // Calculates the total sum of sold items.
    private int getSum() {
        return items.stream().mapToInt(SoldItem::getPrice).sum();
    }
}
