package se.goencoder.loppiskassan.interactor;

import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.*;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.model.history.HistoryState;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.storage.LocalEventRepository;
import se.goencoder.loppiskassan.utils.FileUtils;
import se.goencoder.loppiskassan.utils.FilterUtils;
import se.goencoder.loppiskassan.utils.FilterUtils.FilterResult;
import se.goencoder.loppiskassan.utils.SoldItemUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static se.goencoder.iloppis.model.V1PaidFilter.PAID_FILTER_UNSPECIFIED;
import static se.goencoder.iloppis.model.V1PaymentMethodFilter.PAYMENT_METHOD_FILTER_UNSPECIFIED;

/**
 * Business logic for the history tab: loading, filtering, archiving, and managing sold items.
 * <p>
 * This class has no dependencies on Swing components and is fully testable.
 */
public class HistoryInteractor {

    private static final int PAGE_SIZE = 500;

    private final HistoryState state;
    private List<V1SoldItem> allHistoryItems;

    public HistoryInteractor(HistoryState state) {
        this.state = state;
        this.allHistoryItems = new ArrayList<>();
    }

    /**
     * Loads the entire history from the local file system.
     *
     * @throws IOException if file read fails
     */
    public void loadHistory() throws IOException {
        String eventId = AppModeManager.getEventId();
        if (eventId == null || eventId.isBlank()) {
            allHistoryItems = new ArrayList<>();
        } else {
            LocalEventRepository.ensureEventStorage(eventId);
            Path historyPath = LocalEventPaths.getPendingItemsPath(eventId);
            allHistoryItems = JsonlHelper.readItems(historyPath);
        }

        Set<String> distinctSellers = SoldItemUtils.getDistinctSellers(allHistoryItems);
        state.setAllItems(allHistoryItems);
        state.setDistinctSellers(distinctSellers);
    }

    /**
     * Applies the current filters to the history items and updates state.
     *
     * @param paidFilter the paid filter value
     * @param sellerFilter the seller filter value
     * @param paymentMethodFilter the payment method filter value
     * @return the filtered list of items
     */
    public List<V1SoldItem> applyFilters(String paidFilter, String sellerFilter, String paymentMethodFilter) {
        FilterResult filterResult = FilterUtils.applyFiltersWithSum(
            allHistoryItems,
            paidFilter,
            sellerFilter,
            paymentMethodFilter
        );

        List<V1SoldItem> filteredItems = filterResult.items();

        state.setFilteredItems(filteredItems);
        state.setItemCount(filteredItems.size());
        state.setTotalSum(filterResult.totalSum());

        boolean enablePayout = isPayoutEnabled(filteredItems);
        state.setPayoutEnabled(enablePayout);

        boolean enableArchive = !filteredItems.isEmpty() &&
                filteredItems.stream().allMatch(V1SoldItem::isCollectedBySeller);
        state.setArchiveEnabled(enableArchive);

        return filteredItems;
    }

    /**
     * Clears all history items.
     */
    public void clearAllItems() {
        allHistoryItems.clear();
        state.setAllItems(allHistoryItems);
        state.setDistinctSellers(new HashSet<>());
    }

    /**
     * Saves the current history to the local file.
     *
     * @throws IOException if file write fails
     */
    public void saveHistoryToFile() throws IOException {
        String eventId = AppModeManager.getEventId();
        if (eventId == null || eventId.isBlank()) {
            throw new IOException("Missing event id");
        }
        FileUtils.saveSoldItems(eventId, allHistoryItems);
    }

    /**
     * Marks all filtered items as paid out.
     *
     * @param filteredItems the items to mark as paid
     * @param collectedTime the time they were collected
     */
    public void markItemsAsPaid(List<V1SoldItem> filteredItems, LocalDateTime collectedTime) {
        filteredItems.forEach(item -> item.setCollectedBySellerTime(collectedTime));
    }

    /**
     * Performs a payout via the web API.
     *
     * @param sellerFilter optional seller filter
     * @param paymentMethodFilter optional payment method filter
     * @throws ApiException if the API call fails
     */
    public void payoutWeb(String sellerFilter, String paymentMethodFilter) throws ApiException {
        SoldItemsServicePayoutBody payoutBody = new SoldItemsServicePayoutBody();

        try {
            int seller = Integer.parseInt(sellerFilter);
            payoutBody.setSeller(seller);
        } catch (NumberFormatException e) {
            // Do nothing if seller filter isn't a valid number
        }

        try {
            V1PaymentMethodFilter filter = V1PaymentMethodFilter.fromValue(paymentMethodFilter);
            payoutBody.setPaymentMethodFilter(filter);
        } catch (IllegalArgumentException e) {
            // Do nothing if payment method filter isn't valid
        }

        payoutBody.setUntilTimestamp(OffsetDateTime.now());

        String eventId = AppModeManager.getEventId();
        ApiHelper.INSTANCE.getSoldItemsServiceApi()
                .soldItemsServicePayout(eventId, payoutBody);
    }

    /**
     * Fetches all sold items from the web API in paginated batches.
     *
     * @param eventId the event ID
     * @return map of item ID to sold item
     * @throws ApiException if the API call fails
     */
    public Map<String, V1SoldItem> fetchItemsFromWeb(String eventId) throws ApiException {
        Map<String, V1SoldItem> fetchedItems = new HashMap<>();
        String pageToken = "";
        boolean fetchedAll = false;

        while (!fetchedAll) {
            V1ListSoldItemsResponse result = ApiHelper.INSTANCE.getSoldItemsServiceApi()
                    .soldItemsServiceListSoldItems(
                            eventId,
                            null,                                 // purchaseId
                            PAID_FILTER_UNSPECIFIED.getValue(),   // paidFilter
                            PAYMENT_METHOD_FILTER_UNSPECIFIED.getValue(), // paymentMethodFilter
                            null,                                 // seller
                            Boolean.FALSE,                        // includeArchived
                            Integer.valueOf(PAGE_SIZE),           // pageSize
                            pageToken,                            // nextPageToken
                            "",                                   // prevPageToken
                            Boolean.FALSE                         // includeAggregates
                    );

            result.getItems().forEach(item -> {
                V1SoldItem soldItem = SoldItemUtils.fromApiSoldItem(item, true);
                fetchedItems.put(soldItem.getItemId(), soldItem);
            });

            fetchedAll = result.getNextPageToken() == null || result.getNextPageToken().isEmpty();
            pageToken = result.getNextPageToken();
        }

        return fetchedItems;
    }

    /**
     * Merges fetched items from the web with local history.
     *
     * @param fetchedItems items from the API
     */
    public void mergeFetchedItems(Map<String, V1SoldItem> fetchedItems) {
        Set<String> processedItems = new HashSet<>();

        // First pass: Update existing items
        allHistoryItems.forEach(existingItem -> {
            V1SoldItem fetchedItem = fetchedItems.get(existingItem.getItemId());
            if (fetchedItem != null) {
                existingItem.setCollectedBySellerTime(fetchedItem.getCollectedBySellerTime());
                existingItem.setUploaded(true);
                processedItems.add(existingItem.getItemId());
            }
        });

        // Second pass: Add new items
        fetchedItems.values().forEach(fetchedItem -> {
            if (!processedItems.contains(fetchedItem.getItemId())) {
                allHistoryItems.add(fetchedItem);
            }
        });

        state.setAllItems(allHistoryItems);
        Set<String> distinctSellers = SoldItemUtils.getDistinctSellers(allHistoryItems);
        state.setDistinctSellers(distinctSellers);
    }

    /**
     * Uploads unsynced local items to the web API.
     *
     * @throws ApiException if the API call fails
     */
    public void uploadSoldItems() throws ApiException {
        List<V1SoldItem> unsyncedItems = allHistoryItems.stream()
                .filter(item -> !item.isUploaded())
                .collect(Collectors.toList());

        if (unsyncedItems.isEmpty()) {
            return;
        }

        SoldItemsServiceCreateSoldItemsBody createSoldItems = new SoldItemsServiceCreateSoldItemsBody();
        for (V1SoldItem item : unsyncedItems) {
            se.goencoder.iloppis.model.V1SoldItem apiItem = SoldItemUtils.toApiSoldItem(item);
            createSoldItems.addItemsItem(apiItem);
        }

        String eventId = AppModeManager.getEventId();
        V1CreateSoldItemsResponse response = ApiHelper.INSTANCE
                .getSoldItemsServiceApi()
                .soldItemsServiceCreateSoldItems(eventId, createSoldItems);

        // Mark accepted items as uploaded
        if (response.getAcceptedItems() != null) {
            Set<String> acceptedIds = response.getAcceptedItems().stream()
                    .map(se.goencoder.iloppis.model.V1SoldItem::getItemId)
                    .collect(Collectors.toSet());

            unsyncedItems.forEach(item -> {
                if (acceptedIds.contains(item.getItemId())) {
                    item.setUploaded(true);
                }
            });
        }
    }

    /**
     * Imports sold items from an external file.
     *
     * @param file the file to import from
     * @return result containing added and total counts
     * @throws IOException if file read fails
     */
    public ImportResult importFromFile(File file) throws IOException {
        List<V1SoldItem> importedItems = JsonlHelper.readItems(file.toPath());
        Set<String> existingIds = allHistoryItems.stream()
                .map(V1SoldItem::getItemId)
                .collect(Collectors.toSet());

        long addedCount = 0;
        for (V1SoldItem item : importedItems) {
            if (!existingIds.contains(item.getItemId())) {
                allHistoryItems.add(item);
                addedCount++;
            }
        }

        state.setAllItems(allHistoryItems);
        return new ImportResult(addedCount, importedItems.size());
    }

    /**
     * Removes the specified items from the history.
     *
     * @param itemsToRemove items to remove
     */
    public void removeItems(List<V1SoldItem> itemsToRemove) {
        Set<String> idsToRemove = itemsToRemove.stream()
                .map(V1SoldItem::getItemId)
                .collect(Collectors.toSet());

        allHistoryItems.removeIf(item -> idsToRemove.contains(item.getItemId()));
        state.setAllItems(allHistoryItems);
    }

    /**
     * Generates a text summary of the given items.
     *
     * @param items items to summarize
     * @return formatted summary string
     */
    public String generateSummary(List<V1SoldItem> items) {
        Map<Integer, Integer> sellerTotals = new HashMap<>();
        for (V1SoldItem item : items) {
            sellerTotals.merge(item.getSeller(), item.getPrice(), Integer::sum);
        }

        StringBuilder summary = new StringBuilder();
        sellerTotals.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> summary.append(String.format("Seller %d: %d SEK%n",
                        entry.getKey(), entry.getValue())));

        int grandTotal = items.stream().mapToInt(V1SoldItem::getPrice).sum();
        summary.append(String.format("Total: %d SEK", grandTotal));

        return summary.toString();
    }

    // --- Private helpers ---

    private boolean isPayoutEnabled(List<V1SoldItem> filteredItems) {
        if (filteredItems.isEmpty()) {
            return false;
        }

        boolean hasSellerFilter = state.getSellerFilter() != null &&
                !state.getSellerFilter().isEmpty() &&
                !state.getSellerFilter().equals("all");

        boolean allUnpaid = filteredItems.stream()
                .noneMatch(V1SoldItem::isCollectedBySeller);

        return hasSellerFilter && allUnpaid;
    }

    /**
     * Result of an import operation.
     */
    public static class ImportResult {
        public final long added;
        public final long total;

        public ImportResult(long added, long total) {
            this.added = added;
            this.total = total;
        }
    }
}
