package se.goencoder.loppiskassan.controller;

import se.goencoder.iloppis.model.SoldItemsServiceCreateSoldItemsBody;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.V1CreateSoldItemsResponse;
import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.model.cashier.CashierState;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.storage.LocalEventRepository;
import se.goencoder.loppiskassan.service.CashierStrategy;
import se.goencoder.loppiskassan.service.LocalCashierStrategy;
import se.goencoder.loppiskassan.service.IloppisCashierStrategy;
import se.goencoder.loppiskassan.ui.CashierPanelInterface;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.ui.ProgressDialog;
import se.goencoder.loppiskassan.utils.ConfigurationUtils;
import se.goencoder.loppiskassan.utils.FileUtils;
import se.goencoder.loppiskassan.utils.SoldItemUtils;
import se.goencoder.loppiskassan.utils.UlidGenerator;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.logging.Logger;

import static se.goencoder.loppiskassan.rest.ApiHelper.isLikelyNetworkError;

public class CashierTabController implements CashierControllerInterface {

    private static final CashierTabController instance = new CashierTabController();
    private static final Logger log = Logger.getLogger(CashierTabController.class.getName());

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

    private final List<V1SoldItem> items = new ArrayList<>();
    private final CashierState state = new CashierState();
    private CashierPanelInterface view;
    private CashierStrategy cashierStrategy;

    private CashierTabController() {}

    public static CashierControllerInterface getInstance() {
        return instance;
    }


    /**
     * Get the cashier strategy based on current mode.
     * Lazily initialized and updates when mode changes.
     */
    private CashierStrategy getCashierStrategy() {
        // Reinitialize if mode changed or not yet initialized
        if (cashierStrategy == null || !strategyMatchesCurrentMode()) {
            cashierStrategy = AppModeManager.isLocalMode() 
                ? new LocalCashierStrategy() 
                : new IloppisCashierStrategy();
            log.info("Cashier strategy initialized: " + cashierStrategy.getModeDescription());
        }
        return cashierStrategy;
    }
    
    private boolean strategyMatchesCurrentMode() {
        boolean isLocal = AppModeManager.isLocalMode();
        return (isLocal && cashierStrategy instanceof LocalCashierStrategy)
            || (!isLocal && cashierStrategy instanceof IloppisCashierStrategy);
    }

    /**
     * Get the current cashier state for observers.
     * Views can register PropertyChangeListeners on this state to react to changes.
     *
     * @return the cashier state
     */
    public CashierState getState() {
        return state;
    }

    @Override
    public void registerView(CashierPanelInterface view) {
        this.view = view;
    }

    @Override
    public void onPricesSubmitted() {
        Map<Integer, Integer[]> prices = view.getAndClearSellerPrices();
        prices.forEach(this::addItem);
        view.setFocusToSellerField();
    }

    @Override
    public void onCheckout(V1PaymentMethod paymentMethod) {
        checkout(paymentMethod);
    }

    @Override
    public void onCancelCheckout() {
        cancelCheckout();
    }

    private String logCtx(int sellerId) {
        boolean online = !AppModeManager.isLocalMode();
        String eventId = AppModeManager.getEventId();
        return String.format("event=%s seller=%d mode=%s", eventId, sellerId, online ? "online" : "local");
    }

    // --- Item operations ---
    @Override
    public void deleteItem(String itemId) {
        items.removeIf(item -> {
            if (item.getItemId().equals(itemId)) {
                log.info(() -> String.format("cashier:delete %s", logCtx(item.getSeller())));
                return true;
            }
            return false;
        });
        reCalculate();
    }

    @Override
    public void calculateChange(int payedAmount) {
        int totalSum = getSum();
        int change = payedAmount - totalSum;
        view.setChange(change);
    }

    @Override
    public boolean validateSeller(int sellerId) {
        return getCashierStrategy().validateSeller(sellerId);
    }
    
    @Override
    public String getSellerValidationErrorKey() {
        return getCashierStrategy().getSellerValidationErrorKey();
    }

    public void addItem(Integer sellerId, Integer[] prices) {
        String eventId = AppModeManager.getEventId();
        if (eventId == null || eventId.isBlank()) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("error.no_event_selected.title"),
                    LocalizationManager.tr("error.no_event_selected.message"));
            return;
        }
        try {
            LocalEventRepository.ensureEventStorage(eventId);
        } catch (IOException ex) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("error.save_file", LocalEventPaths.getPendingItemsPath(eventId)),
                    ex.getMessage()
            );
            return;
        }
        log.info(() -> String.format("cashier:add items=%d %s", prices.length, logCtx(sellerId)));
        for (Integer price : prices) {
            V1SoldItem soldItem = new V1SoldItem(sellerId, price, null);
            items.add(soldItem);
            view.addSoldItem(soldItem);
        }
        reCalculate();
    }

    // --- Checkout process ---
    public void checkout(V1PaymentMethod paymentMethod) {
        LocalDateTime now = LocalDateTime.now();
        // Generate a ULID instead of UUID to match the server's expected format ^[0-9A-HJKMNP-TV-Z]{26}$
        String purchaseId = UlidGenerator.generate();
        prepareItemsForCheckout(items, purchaseId, paymentMethod, now);
        
        // Calculate total before clearing
        int totalAmount = getSum();
        
        // Use strategy pattern to persist items
        CashierStrategy strategy = getCashierStrategy();
        
        if (AppModeManager.isLocalMode()) {
            // Local mode: synchronous save
            try {
                strategy.persistItems(items, purchaseId, paymentMethod, now);
                finishCheckoutFlow(paymentMethod, totalAmount);
            } catch (Exception e) {
                Popup.ERROR.showAndWait(
                        LocalizationManager.tr("error.save_file"),
                        e.getMessage()
                );
            }
        } else {
            // iLoppis mode: async with progress dialog
            ProgressDialog.runTask(
                    view.getComponent(),
                    LocalizationManager.tr("cashier.progress.processing"),
                    LocalizationManager.tr("cashier.progress.saving_web"),
                    () -> {
                        strategy.persistItems(items, purchaseId, paymentMethod, now);
                        return null;
                    },
                    unused -> finishCheckoutFlow(paymentMethod, totalAmount),
                    ex -> {
                        if (isLikelyNetworkError(ex)) {
                            degradedMode = true;
                            Popup.warn("warning.degraded_mode");
                        } else {
                            Popup.ERROR.showAndWait(LocalizationManager.tr("error.upload_web"), ex.getMessage());
                        }
                        finishCheckoutFlow(paymentMethod, totalAmount);
                    }
            );
        }
    }

    private void finishCheckoutFlow(V1PaymentMethod paymentMethod, int totalAmount) {
        // 1) Show success notification
        view.showCheckoutSuccess(paymentMethod, totalAmount);

        // 2) Clear the cashier UI
        items.clear();
        view.clearView();
        state.reset();  // Reset state to initial values

        // 4) If we are in degraded mode, spawn a background thread to attempt a catch-up
        boolean isLocal = AppModeManager.isLocalMode();
        if (!isLocal && degradedMode) {
            new Thread(() -> {
                boolean success = pushLocalUnsyncedRecords();
                if (success) {
                    degradedMode = false;
                }
            }).start();
        }
    }

    public void cancelCheckout() {
        items.clear();
        view.clearView();
        state.reset();  // Reset state to initial values
    }

    // --- Recalculate totals ---
    private void reCalculate() {
        int totalSum = getSum();
        int roundedSum = (totalSum + 99) / 100 * 100;
        int change = roundedSum - totalSum;

        view.clearView();
        items.forEach(view::addSoldItem);
        view.setChange(change);
        view.setPaidAmount(roundedSum);
        view.setFocusToSellerField();
        
        // Sync state after recalculation
        syncStateFromItems();
    }

    private int getSum() {
        return items.stream().mapToInt(V1SoldItem::getPrice).sum();
    }

    /**
     * Synchronizes the CashierState with the current items list.
     * Updates item count, total sum, and checkout button enabled state.
     * Called after any operation that modifies the items list.
     */
    private void syncStateFromItems() {
        state.setItems(new ArrayList<>(items));
        state.setItemCount(items.size());
        int total = getSum();
        state.setTotalSum(total);
        state.setCheckoutEnabled(!items.isEmpty());
        
        // Calculate rounded paid amount and change (for Swedish rounding to nearest 100 öre)
        int roundedSum = (total + 99) / 100 * 100;
        state.setPaidAmount(roundedSum);
        state.setChange(roundedSum - total);
        
        // Format strings will be set by the view based on current locale
        // For now, we just update the raw numbers
    }

    // --- Helpers ---
    private void prepareItemsForCheckout(List<V1SoldItem> items, String purchaseId,
                                         V1PaymentMethod paymentMethod, LocalDateTime now) {
        for (V1SoldItem item : items) {
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
    private void saveItemsToWeb(List<V1SoldItem> items) throws ApiException {
        if (items == null || items.isEmpty()) {
            return;
        }

        SoldItemsServiceCreateSoldItemsBody createSoldItems = new SoldItemsServiceCreateSoldItemsBody();
        ZoneOffset currentOffset = OffsetDateTime.now().getOffset();

        Map<String, V1SoldItem> itemMap = items.stream()
                .collect(Collectors.toMap(V1SoldItem::getItemId, x -> x));

        for (V1SoldItem item : items) {
            se.goencoder.iloppis.model.V1SoldItem apiItem = SoldItemUtils.toApiSoldItem(item);
            apiItem.setSoldTime(OffsetDateTime.of(item.getSoldTime(), currentOffset));
            createSoldItems.addItemsItem(apiItem);
        }

        V1CreateSoldItemsResponse response = ApiHelper.INSTANCE
                .getSoldItemsServiceApi()
                .soldItemsServiceCreateSoldItems(AppModeManager.getEventId(), createSoldItems);

        updateLocalItemsStatus(response, itemMap);
    }

    /**
     * Reads the local file, finds all items not uploaded, attempts to upload them.
     * Updates local file based on partial success. Returns true if no network error
     * occurred (i.e. connectivity was OK, even if some items were rejected for data reasons).
     */
    private boolean pushLocalUnsyncedRecords() {
        synchronized (lock) {
            List<V1SoldItem> allItems;
            try {
                String eventId = AppModeManager.getEventId();
                if (eventId == null || eventId.isBlank()) {
                    return false;
                }
                Path localJsonl = LocalEventPaths.getPendingItemsPath(eventId);
                allItems = JsonlHelper.readItems(localJsonl);
            } catch (IOException e) {
                return false;
            }

            List<V1SoldItem> notUploaded = allItems.stream()
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
                String eventId = AppModeManager.getEventId();
                if (eventId == null || eventId.isBlank()) {
                    return false;
                }
                FileUtils.saveSoldItems(eventId, allItems);
            } catch (IOException e) {
                // local file write error is not a reason to remain degraded
                // but we do show a warning
                Popup.warn("warning.update_items", e.getMessage());
            }

            return true;
        }
    }

    private void updateLocalItemsStatus(V1CreateSoldItemsResponse response,
                                        Map<String, V1SoldItem> itemMap) {
        if (response.getAcceptedItems() != null) {
            for (se.goencoder.iloppis.model.V1SoldItem acceptedItem : response.getAcceptedItems()) {
                V1SoldItem localItem = itemMap.get(acceptedItem.getItemId());
                if (localItem != null) {
                    localItem.setUploaded(true);
                }
            }
        }

        if (!Objects.requireNonNull(response.getRejectedItems()).isEmpty()) {
            Popup.warn("warning.partial_upload", response.getRejectedItems());
            for (se.goencoder.iloppis.model.V1RejectedItem rejectedItem : response.getRejectedItems()) {
                V1SoldItem localItem = itemMap.get(rejectedItem.getItem().getItemId());
                if (localItem != null) {
                    localItem.setUploaded(false);
                }
            }
        }
    }


}
