package se.goencoder.loppiskassan.controller;

import org.json.JSONObject;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.CreateSoldItems;
import se.goencoder.iloppis.model.CreateSoldItemsResponse;
import se.goencoder.loppiskassan.PaymentMethod;
import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.records.FileHelper;
import se.goencoder.loppiskassan.records.FormatHelper;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.ui.CashierPanelInterface;
import se.goencoder.loppiskassan.ui.Popup;

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
        checkoutCashButton.addActionListener(e -> checkout(PaymentMethod.Kontant));
    }

    @Override
    public void setupCheckoutSwishButtonAction(JButton checkoutSwishButton) {
        checkoutSwishButton.addActionListener(e -> checkout(PaymentMethod.Swish));
    }

    @Override
    public void setupCancelCheckoutButtonAction(JButton cancelCheckoutButton) {
        cancelCheckoutButton.addActionListener(e -> cancelCheckout());
    }

    @Override
    public void setupPricesTextFieldAction(JTextField pricesTextField) {
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
        items.removeIf(item -> item.getItemId().equals(itemId));
        reCalculate();
    }

    @Override
    public void calculateChange(int payedAmount) {
        int totalSum = getSum();
        int change = payedAmount - totalSum;
        view.updateChangeCashField(change);
    }

    @Override
    public boolean isSellerApproved(int sellerId) {
        if (ConfigurationStore.OFFLINE_EVENT_BOOL.getBooleanValueOrDefault(false)) {
            return true;
        } else {
            // TODO, make this more efficient...
            String approvedSellersJson = ConfigurationStore.APPROVED_SELLERS_JSON.get();
            // convert to JSON Object
            JSONObject jsonObject = new JSONObject(approvedSellersJson);
            // get the array of approved sellers
            return jsonObject.getJSONArray("approvedSellers").toList().contains(sellerId);
        }

    }

    public void addItem(Integer sellerId, Integer[] prices) {
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

        // 1) Prepare items
        prepareItemsForCheckout(items, purchaseId, paymentMethod, now);

        // 2) Save to web if not in offline mode
        boolean isOffline = ConfigurationStore.OFFLINE_EVENT_BOOL.getBooleanValueOrDefault(false);
        if (!isOffline) {
            try {
                saveItemsToWeb(items);
            } catch (Exception e) {
                Popup.ERROR.showAndWait("Kunde inte spara till webb.", e.getMessage());
                return;
            }
        }

        // 3) Save to file & clear
        try {
            saveItemsToFile(items);
            items.clear();
            view.clearView();
        } catch (IOException e) {
            Popup.ERROR.showAndWait("Kunde inte spara till fil (" +
                    FileHelper.getRecordFilePath(LOPPISKASSAN_CSV) + ")", e.getMessage());
        }
    }

    public void cancelCheckout() {
        items.clear();
        view.clearView();
    }

    private void reCalculate() {
        int totalSum = getSum();
        int roundedSum = (totalSum + 99) / 100 * 100; // Round up to the nearest 100
        int change = roundedSum - totalSum;

        // Update the view
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
        return items.stream().mapToInt(SoldItem::getPrice).sum();
    }

    // --- Helper methods to break up checkout() logic ---
    private void prepareItemsForCheckout(List<SoldItem> items, String purchaseId,
                                         PaymentMethod paymentMethod, LocalDateTime now) {
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
        ZoneOffset currentOffset = OffsetDateTime.now().getOffset(); // Corrected to ZoneOffset

        // Convert all items to API items
        for (SoldItem item : items) {
            se.goencoder.iloppis.model.SoldItem apiItem = new se.goencoder.iloppis.model.SoldItem();
            apiItem.setSeller(item.getSeller());
            apiItem.setItemId(item.getItemId());
            apiItem.setPrice(item.getPrice());
            apiItem.setPaymentMethod(
                    item.getPaymentMethod() == PaymentMethod.Kontant
                            ? se.goencoder.iloppis.model.PaymentMethod.KONTANT
                            : se.goencoder.iloppis.model.PaymentMethod.SWISH
            );
            apiItem.setSoldTime(OffsetDateTime.of(item.getSoldTime(), currentOffset)); // Corrected to use ZoneOffset

            createSoldItems.addItemsItem(apiItem);
        }

        // Send to web service
        CreateSoldItemsResponse response = ApiHelper.INSTANCE.getSoldItemsServiceApi().soldItemsServiceCreateSoldItems(
                ConfigurationStore.EVENT_ID_STR.get(),
                createSoldItems
        );

        // Update `uploaded` status for accepted items
        if (response.getAcceptedItems() != null) {
            for (se.goencoder.iloppis.model.SoldItem acceptedItem : response.getAcceptedItems()) {
                SoldItem localItem = itemMap.get(acceptedItem.getItemId());
                if (localItem != null) {
                    localItem.setUploaded(true);
                }
            }
        }
        if (response.getRejectedItems().size() > 0) {
            Popup.WARNING.showAndWait("Några föremål kunde inte laddas upp", response.getRejectedItems());
            for (se.goencoder.iloppis.model.SoldItem rejectedItem : response.getRejectedItems()) {
                SoldItem localItem = itemMap.get(rejectedItem.getItemId());
                if (localItem != null) {
                    localItem.setUploaded(false);
                }
            }
        }
    }


    private void saveItemsToFile(List<SoldItem> items) throws IOException {
        FileHelper.saveToFile(LOPPISKASSAN_CSV, "", FormatHelper.toCVS(items));
    }
}
