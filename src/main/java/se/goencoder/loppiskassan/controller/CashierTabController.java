package se.goencoder.loppiskassan.controller;

import org.json.JSONObject;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.CreateSoldItems;
import se.goencoder.iloppis.model.CreateSoldItemsResponse;
import se.goencoder.loppiskassan.PaymentMethod;
import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.records.FileHelper;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.ui.CashierPanelInterface;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.utils.ConfigurationUtils;
import se.goencoder.loppiskassan.utils.FileUtils;
import se.goencoder.loppiskassan.utils.SoldItemUtils;

import javax.swing.*;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static se.goencoder.loppiskassan.records.FileHelper.LOPPISKASSAN_CSV;

/**
 * Controls the cashier tab, handling the sales process, including item management and checkout procedures.
 */
public class CashierTabController implements CashierControllerInterface {
    private static final CashierTabController instance = new CashierTabController();
    private final List<SoldItem> items = new ArrayList<>();
    private CashierPanelInterface view;

    private CashierTabController() {}

    public static CashierControllerInterface getInstance() {
        return instance;
    }

    // --- Button setup methods ---
    @Override
    public void setupCheckoutCashButtonAction(JButton checkoutCashButton) {
        // Assign a listener to handle cash payment checkouts
        checkoutCashButton.addActionListener(e -> checkout(PaymentMethod.Kontant));
    }

    @Override
    public void setupCheckoutSwishButtonAction(JButton checkoutSwishButton) {
        // Assign a listener to handle Swish payment checkouts
        checkoutSwishButton.addActionListener(e -> checkout(PaymentMethod.Swish));
    }

    @Override
    public void setupCancelCheckoutButtonAction(JButton cancelCheckoutButton) {
        // Assign a listener to handle checkout cancellations
        cancelCheckoutButton.addActionListener(e -> cancelCheckout());
    }

    @Override
    public void setupPricesTextFieldAction(JTextField pricesTextField) {
        // Process seller prices when the action is triggered
        pricesTextField.addActionListener(e -> {
            Map<Integer, Integer[]> prices = view.getAndClearSellerPrices();
            prices.forEach(this::addItem);
            view.setFocusToSellerField();
        });
    }

    @Override
    public void registerView(CashierPanelInterface view) {
        this.view = view;
    }

    // --- Item operations ---
    @Override
    public void deleteItem(String itemId) {
        // Remove the item with the matching ID and update the total
        items.removeIf(item -> item.getItemId().equals(itemId));
        reCalculate();
    }

    @Override
    public void calculateChange(int payedAmount) {
        // Calculate the change based on the total amount and update the view
        int totalSum = getSum();
        int change = payedAmount - totalSum;
        view.updateChangeCashField(change);
    }

    @Override
    public boolean isSellerApproved(int sellerId) {
        // Check if a seller is approved based on the current mode (online/offline)
        if (ConfigurationUtils.isOfflineMode()) {
            return true;
        }

        String approvedSellersJson = ConfigurationStore.APPROVED_SELLERS_JSON.get();
        return new JSONObject(approvedSellersJson)
                .getJSONArray("approvedSellers")
                .toList()
                .contains(sellerId);
    }

    public void addItem(Integer sellerId, Integer[] prices) {
        // Add items to the list only if the file permissions allow it
        if (FileHelper.assertRecordFileRights(LOPPISKASSAN_CSV)) {
            for (Integer price : prices) {
                SoldItem soldItem = new SoldItem(sellerId, price, null);
                items.add(soldItem);
                view.addSoldItem(soldItem);
            }
            reCalculate();
        }
    }

    // --- Checkout process ---
    public void checkout(PaymentMethod paymentMethod) {
        LocalDateTime now = LocalDateTime.now();
        String purchaseId = UUID.randomUUID().toString();

        // Prepare items for the current transaction
        prepareItemsForCheckout(items, purchaseId, paymentMethod, now);

        // Save the transaction to the web if in online mode
        boolean isOffline = ConfigurationStore.OFFLINE_EVENT_BOOL.getBooleanValueOrDefault(false);
        if (!isOffline) {
            try {
                saveItemsToWeb(items);
            } catch (Exception e) {
                Popup.ERROR.showAndWait("Kunde inte spara till webb.", e.getMessage());
                return;
            }
        }

        // Persist the transaction locally and clear the view
        try {
            FileUtils.appendSoldItems(items);
            items.clear();
            view.clearView();
        } catch (IOException e) {
            Popup.ERROR.showAndWait("Kunde inte spara till fil (" +
                    FileHelper.getRecordFilePath(LOPPISKASSAN_CSV) + ")", e.getMessage());
        }
    }

    public void cancelCheckout() {
        // Clear all items and reset the view
        items.clear();
        view.clearView();
    }

    private void reCalculate() {
        // Recalculate the total, rounding it to the nearest 100, and update the UI
        int totalSum = getSum();
        int roundedSum = (totalSum + 99) / 100 * 100;
        int change = roundedSum - totalSum;

        view.clearView();
        items.forEach(view::addSoldItem);
        view.updateSumLabel(String.valueOf(totalSum));
        view.updateNoItemsLabel(String.valueOf(items.size()));
        view.updateChangeCashField(change);
        view.updatePayedCashField(roundedSum);
        view.setFocusToSellerField();
        view.enableCheckoutButtons(!items.isEmpty());
    }

    private int getSum() {
        // Calculate the total sum of the items
        return items.stream().mapToInt(SoldItem::getPrice).sum();
    }

    // --- Helper methods ---
    private void prepareItemsForCheckout(List<SoldItem> items, String purchaseId,
                                         PaymentMethod paymentMethod, LocalDateTime now) {
        // Set relevant details for all items in the current checkout
        items.forEach(item -> {
            item.setSoldTime(now);
            item.setPaymentMethod(paymentMethod);
            item.setPurchaseId(purchaseId);
        });
    }

    private void saveItemsToWeb(List<SoldItem> items) throws ApiException {
        // Map for quick lookup of local items by itemId
        Map<String, SoldItem> itemMap = items.stream()
                .collect(Collectors.toMap(SoldItem::getItemId, item -> item));

        CreateSoldItems createSoldItems = new CreateSoldItems();
        ZoneOffset currentOffset = OffsetDateTime.now().getOffset();

        // Convert all items to API-compatible objects and add them to the batch
        for (SoldItem item : items) {
            se.goencoder.iloppis.model.SoldItem apiItem = SoldItemUtils.toApiSoldItem(item);
            apiItem.setSoldTime(OffsetDateTime.of(item.getSoldTime(), currentOffset));
            createSoldItems.addItemsItem(apiItem);
        }

        // Submit the batch to the web service
        CreateSoldItemsResponse response = ApiHelper.INSTANCE.getSoldItemsServiceApi().soldItemsServiceCreateSoldItems(
                ConfigurationStore.EVENT_ID_STR.get(),
                createSoldItems
        );

        // Update local items based on the response
        updateLocalItemsStatus(response, itemMap);
    }

    private void updateLocalItemsStatus(CreateSoldItemsResponse response, Map<String, SoldItem> itemMap) {
        // Mark accepted items as uploaded
        if (response.getAcceptedItems() != null) {
            for (se.goencoder.iloppis.model.SoldItem acceptedItem : response.getAcceptedItems()) {
                SoldItem localItem = itemMap.get(acceptedItem.getItemId());
                if (localItem != null) {
                    localItem.setUploaded(true);
                }
            }
        }

        // Mark rejected items and notify the user
        if (!Objects.requireNonNull(response.getRejectedItems()).isEmpty()) {
            Popup.WARNING.showAndWait("Några föremål kunde inte laddas upp", response.getRejectedItems());
            for (se.goencoder.iloppis.model.SoldItem rejectedItem : response.getRejectedItems()) {
                SoldItem localItem = itemMap.get(rejectedItem.getItemId());
                if (localItem != null) {
                    localItem.setUploaded(false);
                }
            }
        }
    }
}
