package se.goencoder.loppiskassan.interactor;

import org.json.JSONObject;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.V1CreateSoldItemsResponse;
import se.goencoder.iloppis.model.SoldItemsServiceCreateSoldItemsBody;
import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.config.ILoppisConfigurationStore;
import se.goencoder.loppiskassan.model.cashier.CashierState;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.storage.LocalEventRepository;
import se.goencoder.loppiskassan.utils.ConfigurationUtils;
import se.goencoder.loppiskassan.utils.FileUtils;
import se.goencoder.loppiskassan.utils.SoldItemUtils;
import se.goencoder.loppiskassan.utils.UlidGenerator;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static se.goencoder.loppiskassan.rest.ApiHelper.isLikelyNetworkError;

/**
 * Business logic for the cashier flow: adding items, calculating totals,
 * checking out, and syncing with the server.
 * <p>
 * This class has no dependencies on Swing components and is fully testable.
 */
public class CashierInteractor {
    
    private static final Logger log = Logger.getLogger(CashierInteractor.class.getName());

    private final List<V1SoldItem> items = new ArrayList<>();
    private final CashierState state;
    private final Object lock = new Object();
    private boolean degradedMode = false;

    public CashierInteractor(CashierState state) {
        this.state = state;
    }

    /**
     * Returns whether the system is in degraded mode (network errors causing local-only saves).
     */
    public boolean isDegradedMode() {
        return degradedMode;
    }

    /**
     * Sets the degraded mode flag.
     */
    public void setDegradedMode(boolean degraded) {
        this.degradedMode = degraded;
    }

    /**
     * Adds items for the given seller with the specified prices.
     * Updates the state with the new items and totals.
     *
     * @param sellerId the seller ID
     * @param prices array of prices to add
     * @return true if items were added, false if event setup is missing
     * @throws IOException if storage initialization fails
     */
    public boolean addItems(int sellerId, Integer[] prices) throws IOException {
        String eventId = AppModeManager.getEventId();
        if (eventId == null || eventId.isBlank()) {
            return false;
        }

        LocalEventRepository.ensureEventStorage(eventId);
        
        log.info(() -> String.format("cashier:add items=%d %s", prices.length, logCtx(sellerId)));
        
        for (Integer price : prices) {
            V1SoldItem soldItem = new V1SoldItem(sellerId, price, null);
            items.add(soldItem);
        }
        
        syncState();
        return true;
    }

    /**
     * Removes an item by its ID.
     *
     * @param itemId the item ID to remove
     * @return true if item was found and removed
     */
    public boolean deleteItem(String itemId) {
        boolean removed = items.removeIf(item -> {
            if (item.getItemId().equals(itemId)) {
                log.info(() -> String.format("cashier:delete %s", logCtx(item.getSeller())));
                return true;
            }
            return false;
        });
        
        if (removed) {
            syncState();
        }
        
        return removed;
    }

    /**
     * Returns an immutable copy of the current items list.
     */
    public List<V1SoldItem> getItems() {
        return new ArrayList<>(items);
    }

    /**
     * Calculates the total sum of all items.
     */
    public int calculateTotal() {
        return items.stream().mapToInt(V1SoldItem::getPrice).sum();
    }

    /**
     * Calculates change for a given paid amount against the current total.
     *
     * @param paidAmount the amount paid
     * @return the change amount
     */
    public int calculateChange(int paidAmount) {
        return paidAmount - calculateTotal();
    }

    /**
     * Calculates the rounded paid amount (Swedish rounding to nearest 100 öre)
     * and the resulting change.
     *
     * @return array [roundedPaidAmount, change]
     */
    public int[] calculateRoundedAmounts() {
        int totalSum = calculateTotal();
        int roundedSum = (totalSum + 99) / 100 * 100;
        int change = roundedSum - totalSum;
        return new int[]{roundedSum, change};
    }

    /**
     * Checks if a seller is approved for the current event.
     *
     * @param sellerId the seller ID to check
     * @return true if approved or in local mode
     */
    public boolean isSellerApproved(int sellerId) {
        // If truly local mode, just allow
        if (ConfigurationUtils.isLocalMode()) {
            return true;
        }
        
        String approvedSellersJson = ILoppisConfigurationStore.getApprovedSellers();
        return new JSONObject(approvedSellersJson)
                .getJSONArray("approvedSellers")
                .toList()
                .contains(sellerId);
    }

    /**
     * Prepares items for checkout by setting common fields.
     *
     * @param purchaseId the purchase ID to assign
     * @param paymentMethod the payment method
     * @param soldTime the time of sale
     */
    public void prepareForCheckout(String purchaseId, V1PaymentMethod paymentMethod, LocalDateTime soldTime) {
        for (V1SoldItem item : items) {
            item.setSoldTime(soldTime);
            item.setPaymentMethod(paymentMethod);
            item.setPurchaseId(purchaseId);
        }
    }

    /**
     * Generates a new purchase ID (ULID format).
     */
    public String generatePurchaseId() {
        return UlidGenerator.generate();
    }

    /**
     * Attempts to save items to the web API.
     * Updates each item's uploaded status based on server response.
     *
     * @throws ApiException if the API call fails
     */
    public void saveItemsToWeb() throws ApiException {
        if (items.isEmpty()) {
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

        String eventId = AppModeManager.getEventId();
        V1CreateSoldItemsResponse response = ApiHelper.INSTANCE
                .getSoldItemsServiceApi()
                .soldItemsServiceCreateSoldItems(eventId, createSoldItems);

        updateItemsFromResponse(response, itemMap);
    }

    /**
     * Saves items to the local file system.
     *
     * @throws IOException if file write fails
     */
    public void saveItemsLocally() throws IOException {
        synchronized (lock) {
            String eventId = AppModeManager.getEventId();
            if (eventId == null || eventId.isBlank()) {
                throw new IOException("Missing event id");
            }
            
            // If in degraded mode, mark items as not uploaded
            if (degradedMode) {
                for (V1SoldItem item : items) {
                    item.setUploaded(false);
                }
            }
            
            FileUtils.appendSoldItems(eventId, items);
        }
    }

    /**
     * Clears all items from the interactor and resets state.
     */
    public void clearItems() {
        items.clear();
        state.reset();
    }

    /**
     * Attempts to push unsynced local records to the server.
     * Returns true if connectivity is OK (even if some items were rejected).
     *
     * @return true if network is working, false if degraded mode should continue
     */
    public boolean pushLocalUnsyncedRecords() {
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
                saveItemsToWebBatch(notUploaded);
            } catch (ApiException apiEx) {
                if (isLikelyNetworkError(apiEx)) {
                    return false;
                }
            } catch (Exception ex) {
                if (isLikelyNetworkError(ex)) {
                    return false;
                }
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
                log.warning("Failed to update local items: " + e.getMessage());
            }

            return true;
        }
    }

    // --- Private helpers ---

    private void syncState() {
        state.setItems(new ArrayList<>(items));
        state.setItemCount(items.size());
        int total = calculateTotal();
        state.setTotalSum(total);
        state.setCheckoutEnabled(!items.isEmpty());
        
        int[] amounts = calculateRoundedAmounts();
        state.setPaidAmount(amounts[0]);
        state.setChange(amounts[1]);
    }

    private String logCtx(int sellerId) {
        boolean online = !AppModeManager.isLocalMode();
        String eventId = AppModeManager.getEventId();
        return String.format("event=%s seller=%d mode=%s", eventId, sellerId, online ? "online" : "local");
    }

    private void updateItemsFromResponse(V1CreateSoldItemsResponse response, Map<String, V1SoldItem> itemMap) {
        if (response.getAcceptedItems() != null) {
            for (se.goencoder.iloppis.model.V1SoldItem acceptedItem : response.getAcceptedItems()) {
                V1SoldItem localItem = itemMap.get(acceptedItem.getItemId());
                if (localItem != null) {
                    localItem.setUploaded(true);
                }
            }
        }

        if (!Objects.requireNonNull(response.getRejectedItems()).isEmpty()) {
            for (se.goencoder.iloppis.model.V1RejectedItem rejectedItem : response.getRejectedItems()) {
                V1SoldItem localItem = itemMap.get(rejectedItem.getItem().getItemId());
                if (localItem != null) {
                    localItem.setUploaded(false);
                }
            }
        }
    }

    private void saveItemsToWebBatch(List<V1SoldItem> itemsToSave) throws ApiException {
        if (itemsToSave.isEmpty()) {
            return;
        }

        SoldItemsServiceCreateSoldItemsBody createSoldItems = new SoldItemsServiceCreateSoldItemsBody();
        ZoneOffset currentOffset = OffsetDateTime.now().getOffset();

        Map<String, V1SoldItem> itemMap = itemsToSave.stream()
                .collect(Collectors.toMap(V1SoldItem::getItemId, x -> x));

        for (V1SoldItem item : itemsToSave) {
            se.goencoder.iloppis.model.V1SoldItem apiItem = SoldItemUtils.toApiSoldItem(item);
            apiItem.setSoldTime(OffsetDateTime.of(item.getSoldTime(), currentOffset));
            createSoldItems.addItemsItem(apiItem);
        }

        String eventId = AppModeManager.getEventId();
        V1CreateSoldItemsResponse response = ApiHelper.INSTANCE
                .getSoldItemsServiceApi()
                .soldItemsServiceCreateSoldItems(eventId, createSoldItems);

        updateItemsFromResponse(response, itemMap);
    }
}
