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
import se.goencoder.loppiskassan.utils.ConfigurationUtils;
import se.goencoder.loppiskassan.utils.FileUtils;
import se.goencoder.loppiskassan.utils.SoldItemUtils;

import javax.swing.*;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static se.goencoder.loppiskassan.records.FileHelper.LOPPISKASSAN_CSV;

public class CashierTabController implements CashierControllerInterface {

    // TODO, check so that the buttons to abort/swish/kontant are enabled only if there is at least one item in the list of items (not before that)

    private static final CashierTabController instance = new CashierTabController();

    /**
     * True if the system encountered a network error and is now in "degraded mode".
     * When degraded, we skip attempts to upload new purchases. Instead, we save them
     * locally and spawn a background sync attempt. If that sync succeeds, we switch
     * back to normal (degradedMode = false).
     */
    private static boolean degradedMode = false;

    /**
     * A lock object to synchronize file reads/writes and web push
     * across the main thread and any background threads.
     */
    private final Object lock = new Object();

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
        // If truly offline mode, just allow
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
        boolean isOffline = ConfigurationStore.OFFLINE_EVENT_BOOL.getBooleanValueOrDefault(false);

        LocalDateTime now = LocalDateTime.now();
        String purchaseId = UUID.randomUUID().toString();

        prepareItemsForCheckout(items, purchaseId, paymentMethod, now);

        // 1) If we're in normal mode (not offline, not degraded), try to save these to the web
        if (!isOffline && !degradedMode) {
            try {
                saveItemsToWeb(items);
            } catch (Exception e) {
                // If we detect a network-related failure
                if (isLikelyNetworkError(e)) {
                    degradedMode = true;
                    Popup.WARNING.showAndWait(
                            "Nätverksfel - Kassa i degraderat läge",
                            "Kunde inte spara till webb. Vi går in i degraderat läge.\n"
                                    + "Alla köp sparas lokalt tills vi kan synkronisera igen.\n\n"
                                    + "Du kan göra en manuell synk i Historik-fliken."
                    );
                } else {
                    // Probably an APIException with data validation issues - let's show to user
                    Popup.ERROR.showAndWait("Kunde inte spara till webb", e.getMessage());
                }
            }
        }

        // 2) Regardless of whether we uploaded or not, always save locally
        //    If degrade was triggered, we only have local copy for sure.
        synchronized (lock) {
            try {
                FileUtils.appendSoldItems(items);
            } catch (IOException e) {
                Popup.ERROR.showAndWait(
                        "Kunde inte spara till fil (" + FileHelper.getRecordFilePath(LOPPISKASSAN_CSV) + ")",
                        e.getMessage()
                );
            }
        }

        // Clear the cashier UI
        items.clear();
        view.clearView();

        // 3) If we are in degraded mode, spawn a background thread to attempt a catch-up
        if (!isOffline && degradedMode) {
            new Thread(() -> {
                boolean success = pushLocalUnsyncedRecords();
                if (success) {
                    // Switch out of degraded mode
                    degradedMode = false;
                }
            }).start();
        }
    }

    public void cancelCheckout() {
        items.clear();
        view.clearView();
    }

    // --- Recalculate totals ---
    private void reCalculate() {
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
        return items.stream().mapToInt(SoldItem::getPrice).sum();
    }

    // --- Helpers ---
    private void prepareItemsForCheckout(List<SoldItem> items, String purchaseId,
                                         PaymentMethod paymentMethod, LocalDateTime now) {
        for (SoldItem item : items) {
            item.setSoldTime(now);
            item.setPaymentMethod(paymentMethod);
            item.setPurchaseId(purchaseId);
        }
    }

    /**
     * Attempts to upload the given items to the web in a single batch request.
     * <p>
     * Note: This method does NOT handle concurrency by itself. Make sure you
     * synchronize if reading from or modifying shared structures.
     *
     * @throws ApiException if there's an API-level or partial success
     * @throws RuntimeException or other exceptions for network connectivity errors
     *                          (some might bubble as ApiException with code=0)
     */
    private void saveItemsToWeb(List<SoldItem> items) throws ApiException {
        // TODO: set an API call timeout of 5 seconds for this request in your HTTP client config

        if (items == null || items.isEmpty()) {
            return;
        }

        CreateSoldItems createSoldItems = new CreateSoldItems();
        ZoneOffset currentOffset = OffsetDateTime.now().getOffset();

        Map<String, SoldItem> itemMap = items.stream()
                .collect(Collectors.toMap(SoldItem::getItemId, x -> x));

        for (SoldItem item : items) {
            se.goencoder.iloppis.model.SoldItem apiItem = SoldItemUtils.toApiSoldItem(item);
            apiItem.setSoldTime(OffsetDateTime.of(item.getSoldTime(), currentOffset));
            createSoldItems.addItemsItem(apiItem);
        }

        CreateSoldItemsResponse response = ApiHelper.INSTANCE
                .getSoldItemsServiceApi()
                .soldItemsServiceCreateSoldItems(ConfigurationStore.EVENT_ID_STR.get(), createSoldItems);

        updateLocalItemsStatus(response, itemMap);
    }

    /**
     * Reads the local file, finds all items not uploaded, attempts to upload them.
     * Updates local file based on partial success. Returns true if no network error
     * occurred (i.e. connectivity was OK, even if some items were rejected for data reasons).
     */
    private boolean pushLocalUnsyncedRecords() {
        synchronized (lock) {
            List<SoldItem> allItems;
            try {
                Path localCsv = FileHelper.getRecordFilePath(LOPPISKASSAN_CSV);
                allItems = FormatHelper.toItems(FileHelper.readFromFile(localCsv), true);
            } catch (IOException e) {
                return false;
            }

            List<SoldItem> notUploaded = allItems.stream()
                    .filter(item -> !item.isUploaded())
                    .collect(Collectors.toList());

            if (notUploaded.isEmpty()) {
                return true; // Nothing to push, so "success" in terms of connectivity
            }

            try {
                saveItemsToWeb(notUploaded);
            } catch (ApiException apiEx) {
                // If code=0 or other sign of no connectivity => degrade remains
                if (isLikelyNetworkError(apiEx)) {
                    return false;
                }
                // Otherwise partial success or data error => keep going
            } catch (Exception ex) {
                // Non-ApiException network or other error => degrade remains
                if (isLikelyNetworkError(ex)) {
                    return false;
                }
                // Some other unknown error => treat as connectivity for safety
                return false;
            }

            // On partial success, some items may now be uploaded
            // -> re-save entire file with updated statuses
            try {
                FileUtils.saveSoldItems(allItems);
            } catch (IOException e) {
                // local file write error is not a reason to remain degraded
                // but we do show a warning
                Popup.WARNING.showAndWait("Varning", "Kunde inte uppdatera filstatus för uppladdade poster: " + e.getMessage());
            }

            return true;
        }
    }

    private void updateLocalItemsStatus(CreateSoldItemsResponse response,
                                        Map<String, SoldItem> itemMap) {
        if (response.getAcceptedItems() != null) {
            for (se.goencoder.iloppis.model.SoldItem acceptedItem : response.getAcceptedItems()) {
                SoldItem localItem = itemMap.get(acceptedItem.getItemId());
                if (localItem != null) {
                    localItem.setUploaded(true);
                }
            }
        }

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

    /**
     * Heuristic to detect a network or connectivity type error.
     * You can expand this logic depending on how your `ApiException` is structured.
     */
    private boolean isLikelyNetworkError(Exception e) {
        if (e instanceof ApiException apiEx) {
            // code=0 often indicates a connection failure or unknown host
            return apiEx.getCode() == 0;
        }
        // Could be UnknownHostException, SocketTimeoutException, etc.
        // For brevity, treat everything else as a potential network error.
        return true;
    }
}
