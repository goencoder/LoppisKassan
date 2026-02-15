package se.goencoder.loppiskassan.controller;

import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.model.cashier.CashierState;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.storage.LocalEventRepository;
import se.goencoder.loppiskassan.service.CashierStrategy;
import se.goencoder.loppiskassan.service.LocalCashierStrategy;
import se.goencoder.loppiskassan.service.IloppisCashierStrategy;
import se.goencoder.loppiskassan.service.BackgroundSyncManager;
import se.goencoder.loppiskassan.ui.CashierPanelInterface;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.utils.UlidGenerator;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class CashierTabController implements CashierControllerInterface {

    private static final CashierTabController instance = new CashierTabController();
    private static final Logger log = Logger.getLogger(CashierTabController.class.getName());

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
            // iLoppis mode: local-first, enqueue for background sync
            try {
                strategy.persistItems(items, purchaseId, paymentMethod, now);
                finishCheckoutFlow(paymentMethod, totalAmount);
            } catch (Exception e) {
                Popup.ERROR.showAndWait(
                        LocalizationManager.tr("error.upload_web"),
                        e.getMessage()
                );
            }
        }
    }

    private void finishCheckoutFlow(V1PaymentMethod paymentMethod, int totalAmount) {
        // 1) Show success notification
        view.showCheckoutSuccess(paymentMethod, totalAmount);

        // 2) Clear the cashier UI
        items.clear();
        view.clearView();
        state.reset();  // Reset state to initial values

        String eventId = AppModeManager.getEventId();
        // 4) Ensure background sync is running (non-local mode)
        boolean isLocal = AppModeManager.isLocalMode();
        if (!isLocal && eventId != null && !eventId.isBlank()) {
            BackgroundSyncManager.getInstance().ensureRunning(eventId);
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

}
