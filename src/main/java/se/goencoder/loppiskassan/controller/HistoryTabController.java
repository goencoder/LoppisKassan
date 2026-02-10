package se.goencoder.loppiskassan.controller;

import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.*;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.config.ILoppisConfigurationStore;
import se.goencoder.loppiskassan.config.LocalConfigurationStore;
import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.model.history.HistoryState;
import se.goencoder.loppiskassan.records.FormatHelper;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.storage.LocalEventRepository;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.ui.HistoryPanelInterface;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.ui.ProgressDialog;
import se.goencoder.loppiskassan.ui.dialogs.DestructiveConfirmationDialog;
import se.goencoder.loppiskassan.utils.FileUtils;
import se.goencoder.loppiskassan.utils.FilterUtils;
import se.goencoder.loppiskassan.utils.FilterUtils.FilterResult;
import se.goencoder.loppiskassan.utils.SoldItemUtils;
import se.goencoder.loppiskassan.localization.LocalizationManager;

import se.goencoder.loppiskassan.service.UIThreadingService;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static se.goencoder.iloppis.model.V1PaidFilter.PAID_FILTER_UNSPECIFIED;
import static se.goencoder.iloppis.model.V1PaymentMethodFilter.PAYMENT_METHOD_FILTER_UNSPECIFIED;
import static se.goencoder.loppiskassan.ui.Constants.*;

/**
 * Controls the history tab, handling the display, filtering, and management of sold items.
 */
public class HistoryTabController implements HistoryControllerInterface {
    private static final HistoryTabController instance = new HistoryTabController();

    // Constants for API queries and timing tolerance
    private static final int PAGE_SIZE = 500;
    private static final int SOLD_TIME_TOLERANCE_SECONDS = 60;

    private HistoryPanelInterface view;
    private List<V1SoldItem> allHistoryItems;
    private final HistoryState state = new HistoryState();
    private se.goencoder.loppiskassan.service.EventService eventService;

    private HistoryTabController() {}

    public static HistoryTabController getInstance() {
        return instance;
    }

    /**
     * Get the event service, lazily initializing if needed.
     */
    private se.goencoder.loppiskassan.service.EventService getEventService() {
        if (eventService == null) {
            eventService = se.goencoder.loppiskassan.service.EventServiceFactory.getEventService();
        }
        return eventService;
    }

    /**
     * Get the current history state for observers.
     * Views can register PropertyChangeListeners on this state to react to changes.
     *
     * @return the history state
     */
    public HistoryState getState() {
        return state;
    }

    @Override
    public void registerView(HistoryPanelInterface view) {
        this.view = view;
        // Reset cached event service so it re-evaluates local/online mode
        this.eventService = null;
    }

    @Override
    public void loadHistory() {
        // Ensure import button visibility matches current mode on every load
        updateImportButton();

        java.nio.file.Path historyPath = null;
        try {
            // Load the history from the local JSONL file and populate the seller dropdown with distinct sellers.
            String eventId = AppModeManager.getEventId();
            if (eventId == null || eventId.isBlank()) {
                allHistoryItems = new ArrayList<>();
            } else {
                LocalEventRepository.ensureEventStorage(eventId);
                historyPath = LocalEventPaths.getPendingItemsPath(eventId);
                allHistoryItems = JsonlHelper.readItems(historyPath);
            }
            Set<String> distinctSellers = SoldItemUtils.getDistinctSellers(allHistoryItems);
            
            // Update state
            state.setAllItems(allHistoryItems);
            state.setDistinctSellers(distinctSellers);
            
            UIThreadingService.invokeLater(() -> view.updateSellerDropdown(distinctSellers));
        } catch (IOException e) {
            String pathInfo = historyPath == null ? e.getMessage() : historyPath.toString();
            Popup.FATAL.showAndWait(
                    LocalizationManager.tr("error.read_register_file", pathInfo),
                    e.getMessage());
        }
    }

    @Override
    public void filterUpdated() {
        updateImportButton();

        // Apply the current filters and update the view accordingly.
        FilterResult filterResult = applyFiltersWithResult();
        List<V1SoldItem> filteredItems = filterResult.items();

        // Update state with filtered results
        state.setFilteredItems(filteredItems);
        state.setItemCount(filteredItems.size());
        int sum = filterResult.totalSum();
        state.setTotalSum(sum);

        boolean isLocal = AppModeManager.isLocalMode();
        boolean enablePayout = isLocal && isPayoutEnabled(filteredItems);
        state.setPayoutEnabled(enablePayout);

        boolean enableArchive = isLocal && isArchiveEnabled();
        state.setArchiveEnabled(enableArchive);

        // Update view (keeping existing behavior)
        view.updateHistoryTable(filteredItems);
        view.updateNoItemsLabel(String.valueOf(filteredItems.size()));
        view.updateSumLabel(String.valueOf(sum));
        view.enableButton(BUTTON_PAY_OUT, enablePayout);
        view.enableButton(BUTTON_ARCHIVE, enableArchive);
    }

    @Override
    public void buttonAction(String actionCommand) {
        switch (actionCommand) {
            case BUTTON_ERASE -> clearData();
            case BUTTON_IMPORT -> handleImportAction();
            case BUTTON_PAY_OUT -> { if (getEventService().isLocal()) payout(); }
            case BUTTON_COPY_TO_CLIPBOARD -> copyToClipboard();
            case BUTTON_ARCHIVE -> { if (getEventService().isLocal()) archiveFilteredItems(); }
            default -> throw new IllegalStateException("Unexpected action: " + actionCommand);
        }
    }

    private void downloadSoldItems() {
        // Retrieve all sold items from the web, merge with local items, and save them to the local file.
        String eventId = AppModeManager.getEventId();
        Map<String, V1SoldItem> fetchedItems = fetchItemsFromWeb(eventId);
        mergeFetchedItems(fetchedItems);
        saveHistoryToFile();
        updateDistinctSellers();
        filterUpdated();
    }

    private void clearData() {
        // Clear all local data after user confirmation using destructive dialog.
        java.awt.Frame owner = (java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(view.getComponent());
        boolean confirmed = DestructiveConfirmationDialog.show(
                owner,
                LocalizationManager.tr(BUTTON_ERASE),
                LocalizationManager.tr("confirm.erase_register"),
                "RADERA");
        
        if (confirmed) {
            allHistoryItems.clear();
            saveHistoryToFile();
            filterUpdated();
        }
    }

    private void importData() {
        // Import sold items from one or more external files selected by the user.
        File[] files = selectFilesForImport();
        if (files == null || files.length == 0) {
            return;
        }

        long addedCount = 0;
        long totalCount = 0;

        try {
            for (File file : files) {
                ImportResult result = importSoldItemsFromFile(file);
                addedCount += result.added;
                totalCount += result.total;
            }
        } catch (Exception e) {
            Popup.ERROR.showAndWait(LocalizationManager.tr("error.import_failed"), e.getMessage());
            return;
        }

        saveHistoryToFile();
        filterUpdated();

        Popup.INFORMATION.showAndWait(
                LocalizationManager.tr("info.import_done.title"),
                LocalizationManager.tr("info.import_done.message", addedCount, totalCount));
    }

    private void archiveFilteredItems() {
        // Archive ALL paid out items (regardless of filter) to a CSV file and remove them from the history list.
        List<V1SoldItem> paidItems = allHistoryItems.stream()
                .filter(V1SoldItem::isCollectedBySeller)
                .collect(Collectors.toList());

        if (paidItems.isEmpty()) {
            return;
        }

        // Count unique sellers
        long uniqueSellers = paidItems.stream()
                .map(V1SoldItem::getSeller)
                .distinct()
                .count();

        // Show confirmation dialog with statistics
        String confirmMessage = LocalizationManager.tr(
                "confirm.archive_paid_items",
                paidItems.size(),
                uniqueSellers);

        if (!Popup.CONFIRM.showConfirmDialog(
                LocalizationManager.tr(BUTTON_ARCHIVE),
                confirmMessage)) {
            return;
        }

        archiveItemsToFile(paidItems);
        removeFilteredItems(paidItems);
        saveHistoryToFile();
        // Refresh UI components
        updateDistinctSellers();
        filterUpdated();
    }

    private void updateDistinctSellers() {
        Set<String> distinctSellers = SoldItemUtils.getDistinctSellers(allHistoryItems);
            UIThreadingService.invokeLater(() -> view.updateSellerDropdown(distinctSellers));
    }

    private void payout() {
        // Mark filtered items as paid out and update the history.
        List<V1SoldItem> filteredItems = applyFilters();
        LocalDateTime now = LocalDateTime.now();

        filteredItems.forEach(item -> item.setCollectedBySellerTime(now));
        try {
            // Delegate to service (online mode calls API, local mode no-op)
            String eventId = AppModeManager.getEventId();
            getEventService().performPayout(
                eventId,
                view.getSellerFilter(),
                view.getPaymentMethodFilter()
            );
            saveHistoryToFile();
            filterUpdated();
        } catch (ApiException e) {
            Popup.ERROR.showAndWait(LocalizationManager.tr("error.payout"), e.getMessage());
        }
    }

    private void copyToClipboard() {
        // Copy a summary of filtered items to the system clipboard.
        List<V1SoldItem> filteredItems = applyFilters();
        String summary = generateSummary(filteredItems);

        view.copyToClipboard(summary);
    }

    private List<V1SoldItem> applyFilters() {
        // Apply the current filters to the history items.
        return applyFiltersWithResult().items();
    }

    private FilterResult applyFiltersWithResult() {
        return FilterUtils.applyFiltersWithSum(
                allHistoryItems,
                view.getPaidFilter(),
                view.getSellerFilter(),
                view.getPaymentMethodFilter()
        );
    }

    private void handleImportAction() {
        // Create context with both local and online operations - service chooses which to use
        se.goencoder.loppiskassan.service.SyncContext context =
            new se.goencoder.loppiskassan.service.SyncContext(
                view.getComponent(),
                this::importData,  // Local mode operation
                () -> {  // Online mode operation
                    boolean uploadSucceeded = false;
                    boolean downloadSucceeded = false;
                    
                    try {
                        uploadSucceeded = uploadSoldItems();
                    } catch (Exception uploadError) {
                        // Upload failed - show specific error
                        Popup.ERROR.showAndWait(
                            LocalizationManager.tr("error.network_upload.title"),
                            LocalizationManager.tr("error.network_upload.message") + "\\n" + uploadError.getMessage()
                        );
                    }
                    
                    try {
                        downloadSoldItems();
                        downloadSucceeded = true;
                    } catch (Exception downloadError) {
                        // Download failed - show specific error (only if upload succeeded or wasn't attempted)
                        Popup.ERROR.showAndWait(
                            LocalizationManager.tr("error.network_fetch_history.title"),
                            LocalizationManager.tr("error.network_fetch_history.message") + "\\n" + downloadError.getMessage()
                        );
                    }
                    
                    // Show success if at least one operation succeeded
                    if (uploadSucceeded || downloadSucceeded) {
                        StringBuilder successMsg = new StringBuilder();
                        if (uploadSucceeded) {
                            successMsg.append(LocalizationManager.tr("info.upload_success"));
                        }
                        if (downloadSucceeded) {
                            if (successMsg.length() > 0) successMsg.append(" ");
                            successMsg.append(LocalizationManager.tr("info.download_success"));
                        }
                        if (successMsg.length() > 0) {
                            Popup.INFORMATION.showAndWait(
                                LocalizationManager.tr("info.sync_complete"),
                                successMsg.toString()
                            );
                        }
                    }
                    
                    return null;
                },
                LocalizationManager.tr("history.progress.updating_items"),
                LocalizationManager.tr("history.progress.syncing")
            );
        
        try {
            getEventService().synchronizeItems(context);
        } catch (Exception e) {
            // This catch is for service-level errors, specific operation errors are handled above
            Popup.ERROR.showAndWait(
                LocalizationManager.tr("error.sync_failed"),
                e.getMessage()
            );
        }
    }

    private Map<String, V1SoldItem> fetchItemsFromWeb(String eventId) {
        // Fetch items from the web service.
        Map<String, V1SoldItem> fetchedItems = new HashMap<>();
        try {
            String pageToken = "";
            boolean fetchedAll = false;

            while (!fetchedAll) {
                V1ListSoldItemsResponse result = ApiHelper.INSTANCE.getSoldItemsServiceApi()
                        .soldItemsServiceListSoldItems(
                                eventId,                              // eventId
                                null,                                 // purchaseId
                                PAID_FILTER_UNSPECIFIED.getValue(),   // paidFilter
                                PAYMENT_METHOD_FILTER_UNSPECIFIED.getValue(), // paymentMethodFilter
                                null,                                 // seller
                                Boolean.FALSE,                        // includeArchived
                                Integer.valueOf(PAGE_SIZE),                 // pageSize
                                pageToken,                           // nextPageToken
                                "",                                  // prevPageToken
                                Boolean.FALSE                        // includeAggregates
                        );

                result.getItems().forEach(item -> {
                    V1SoldItem soldItem = SoldItemUtils.fromApiSoldItem(item, true);
                    fetchedItems.put(soldItem.getItemId(), soldItem);
                });

                fetchedAll = result.getNextPageToken() == null || result.getNextPageToken().isEmpty();
                pageToken = result.getNextPageToken();
            }
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }

        return fetchedItems;
    }

    private void mergeFetchedItems(Map<String, V1SoldItem> fetchedItems) {
        // Track items that have been added to prevent duplicates
        Set<String> processedItems = new HashSet<>();

        // First pass: Update existing items
        allHistoryItems.forEach(existingItem -> {
            V1SoldItem fetchedItem = fetchedItems.get(existingItem.getItemId());
            if (fetchedItem != null) {
                // Update existing item
                existingItem.setCollectedBySellerTime(fetchedItem.getCollectedBySellerTime());
                existingItem.setUploaded(fetchedItem.isUploaded());
                processedItems.add(existingItem.getItemId());
            }
        });

        // Second pass: Add new items, but check for potential duplicates
        for (V1SoldItem fetchedItem : fetchedItems.values()) {
            if (processedItems.contains(fetchedItem.getItemId())) {
                // Already processed this item
                continue;
            }

            // Check for potential duplicate by matching seller, price, and close timestamp
            boolean isDuplicate = allHistoryItems.stream().anyMatch(existingItem ->
                    existingItem.getSeller() == fetchedItem.getSeller() &&
                    existingItem.getPrice() == fetchedItem.getPrice() &&
                    isSameTimeApproximately(existingItem.getSoldTime(), fetchedItem.getSoldTime(), SOLD_TIME_TOLERANCE_SECONDS) // 60 seconds tolerance
            );

            if (!isDuplicate) {
                allHistoryItems.add(fetchedItem);
            }
        }
    }

    /**
     * Helper method to check if two timestamps are approximately the same
     * @param time1 First timestamp
     * @param time2 Second timestamp
     * @param toleranceSeconds Maximum difference in seconds
     * @return true if timestamps are within tolerance
     */
    private boolean isSameTimeApproximately(LocalDateTime time1, LocalDateTime time2, long toleranceSeconds) {
        if (time1 == null || time2 == null) {
            return false;
        }

        long diffSeconds = Math.abs(java.time.Duration.between(time1, time2).getSeconds());
        return diffSeconds <= toleranceSeconds;
    }

    private void saveHistoryToFile() {
        java.nio.file.Path historyPath = null;
        try {
            String eventId = AppModeManager.getEventId();
            if (eventId == null || eventId.isBlank()) {
                return;
            }
            historyPath = LocalEventPaths.getPendingItemsPath(eventId);
            FileUtils.saveSoldItems(eventId, allHistoryItems);
        } catch (IOException e) {
            String pathInfo = historyPath == null ? e.getMessage() : historyPath.toString();
            Popup.FATAL.showAndWait(
                    LocalizationManager.tr("error.write_register_file", pathInfo),
                    e.getMessage());
        }
    }

    private void archiveItemsToFile(List<V1SoldItem> paidItems) {
        // Save paid items to an archive file with a timestamped filename in event-specific directory.
        String eventId = AppModeManager.getEventId();
        if (eventId == null || eventId.isEmpty()) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("error.no_event_selected"),
                    LocalizationManager.tr("error.select_event_first"));
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yy-MM-dd_HH-mm-ss"));
        String fileName = LocalizationManager.tr("history.archive_prefix") + timestamp + ".csv";

        try {
            // Create event-specific archive directory if it doesn't exist
            Path archiveDir = LocalEventPaths.getArchiveDir(eventId);
            Files.createDirectories(archiveDir);

            // Save archive file to event-specific directory
            Path archivePath = archiveDir.resolve(fileName);
            saveArchiveFile(archivePath, FormatHelper.toCVS(paidItems));
        } catch (IOException e) {
            Popup.FATAL.showAndWait(
                    LocalizationManager.tr("error.archive_file", fileName),
                    e.getMessage());
        }
    }

    private void saveArchiveFile(Path path, String csv) throws IOException {
        // Save archive CSV file
        try (OutputStream outputStream = new BufferedOutputStream(
                Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
            // Write CSV headers
            outputStream.write(FormatHelper.CVS_HEADERS.getBytes(StandardCharsets.UTF_8));
            outputStream.write(FormatHelper.LINE_ENDING.getBytes(StandardCharsets.UTF_8));

            // Write CSV data
            outputStream.write(csv.getBytes(StandardCharsets.UTF_8));
        }
    }

    private void removeFilteredItems(List<V1SoldItem> filteredItems) {
        // Remove archived items from the history list.
        Set<String> filteredItemIds = filteredItems.stream()
                .map(V1SoldItem::getItemId)
                .collect(Collectors.toSet());

        allHistoryItems.removeIf(item -> filteredItemIds.contains(item.getItemId()));
    }

    private String generateSummary(List<V1SoldItem> filteredItems) {
        // Generate a CSV-like summary with headers + localized totals.
        int totalItems = filteredItems.size();
        int totalSum = filteredItems.stream().mapToInt(V1SoldItem::getPrice).sum();

        StringBuilder summary = new StringBuilder();
        summary.append(LocalizationManager.tr("history.summary.header"));
        summary.append(LocalizationManager.tr("history.summary.items", totalItems, totalSum));
        summary.append('\n');
        summary.append(LocalizationManager.tr("history.summary.csv_header"));
        summary.append('\n');

        filteredItems.forEach(item -> summary.append(formatItemForClipboard(item)).append('\n'));

        return summary.toString();
    }

    private String formatItemForClipboard(V1SoldItem item) {
        // Format a single item as CSV row for clipboard export
        String soldAt = item.getSoldTime() == null
                ? ""
                : item.getSoldTime().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String paidOut = item.isCollectedBySeller()
                ? LocalizationManager.tr("common.yes")
                : LocalizationManager.tr("common.no");
        String payment = LocalizationManager.tr(item.getPaymentMethod() == se.goencoder.loppiskassan.V1PaymentMethod.Kontant
                ? "payment.cash"
                : "payment.swish");

        return String.join(",",
                String.valueOf(item.getSeller()),
                String.valueOf(item.getPrice()),
                soldAt,
                paidOut,
                payment);
    }

    private boolean uploadSoldItems() {
        // 1) Hitta alla poster som inte redan är uppladdade
        List<V1SoldItem> notUploaded = allHistoryItems.stream()
                .filter(item -> !item.isUploaded())
                .collect(Collectors.toList());

        if (notUploaded.isEmpty()) {
            // Inget att ladda upp => returnera true, allt "ok"
            return true;
        }

        List<V1RejectedItem> allRejected = new ArrayList<>();
        boolean networkError = false; // För att särskilja nätverksfel

        // 2) Kör i sub‐batchar om 100 poster
        for (int i = 0; i < notUploaded.size(); i += 100) {
            int end = Math.min(i + 100, notUploaded.size());
            List<V1SoldItem> subBatch = notUploaded.subList(i, end);

            try {
                // 2.1) Skicka upp subBatch
                List<V1RejectedItem> rejectedItems = uploadBatch(subBatch);
                // 2.2) Spara eventuella avvisade
                allRejected.addAll(rejectedItems);

            } catch (ApiException e) {
                // 3) Skilj på nätverksfel och "riktiga" API-fel
                if (ApiHelper.isLikelyNetworkError(e)) {
                    // Avbryt uppladdning helt
                    networkError = true;
                    break;
                } else {
                    Popup.ERROR.showAndWait(LocalizationManager.tr("error.upload_web"), e.getMessage());
                    break;
                }
            }
        }

        // 4) Spara ner de eventuella lyckade uppladdningar som utfördes *innan* ev. fel
        saveHistoryToFile();

        // 5) Om vi träffade nätverksfel => visa popup, return false
        if (networkError) {
            // Möjligen vill du också räkna hur många sub‐batchar som redan hade laddats upp innan felet.
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("error.network_upload.title"),
                    LocalizationManager.tr("error.network_upload.message"));
            return false;
        }

        // 6) Show rejected items due to API errors
        if (!allRejected.isEmpty()) {
            StringBuilder msg = new StringBuilder(LocalizationManager.tr("error.upload_rejected.header"));
            for (V1RejectedItem rejectedItem : allRejected) {
                msg.append(LocalizationManager.tr(
                        "error.upload_rejected.entry",
                        rejectedItem.getItem().getItemId(),
                        rejectedItem.getItem().getSeller(),
                        rejectedItem.getItem().getPrice(),
                        rejectedItem.getItem().getPaymentMethod(),
                        rejectedItem.getReason()));
            }
            Popup.ERROR.showAndWait(LocalizationManager.tr("error.upload_rejected"), msg.toString());
            return false;
        }

        // 7) Kommer vi hit har vi varken nätverksfel eller avvisade poster => allt gick bra
        return true;
    }

    private List<V1RejectedItem> uploadBatch(List<V1SoldItem> batch) throws ApiException {
        // Group items by purchase ID to ensure server compatibility
        Map<String, List<V1SoldItem>> purchaseGroups = batch.stream()
                .collect(Collectors.groupingBy(item -> {
                    String purchaseId = item.getPurchaseId();
                    if (purchaseId == null || purchaseId.trim().isEmpty()) {
                        purchaseId = se.goencoder.loppiskassan.utils.UlidGenerator.generate();
                        item.setPurchaseId(purchaseId);
                    }
                    return purchaseId;
                }));

        List<V1RejectedItem> allRejected = new ArrayList<>();

        // Upload each purchase group separately to avoid "purchaseId mismatch" errors
        for (List<V1SoldItem> purchaseItems : purchaseGroups.values()) {
            SoldItemsServiceCreateSoldItemsBody requestBody = new SoldItemsServiceCreateSoldItemsBody();

            for (V1SoldItem localItem : purchaseItems) {
                se.goencoder.iloppis.model.V1SoldItem apiItem = SoldItemUtils.toApiSoldItem(localItem);
                apiItem.setSoldTime(OffsetDateTime.of(
                        localItem.getSoldTime(),
                        OffsetDateTime.now().getOffset()
                ));
                requestBody.addItemsItem(apiItem);
            }

            V1CreateSoldItemsResponse response = ApiHelper.INSTANCE.getSoldItemsServiceApi()
                    .soldItemsServiceCreateSoldItems(AppModeManager.getEventId(), requestBody);

            // Mark accepted items as uploaded
            if (response.getAcceptedItems() != null) {
                Map<String, V1SoldItem> localMap = purchaseItems.stream()
                        .collect(Collectors.toMap(V1SoldItem::getItemId, Function.identity()));

                for (se.goencoder.iloppis.model.V1SoldItem accepted : response.getAcceptedItems()) {
                    V1SoldItem localItem = localMap.get(accepted.getItemId());
                    if (localItem != null) {
                        localItem.setUploaded(true);
                    }
                }
            }

            // Collect rejected items
            if (response.getRejectedItems() != null) {
                allRejected.addAll(response.getRejectedItems());
            }
        }

        return allRejected;
    }

    private boolean isPayoutEnabled(List<V1SoldItem> filteredItems) {
        // Enable payout if there are unpaid items for the selected seller.
        return filteredItems.stream().anyMatch(item -> !item.isCollectedBySeller());
    }

    private boolean isArchiveEnabled() {
        // Enable archive if there is at least one paid item in all history
        return allHistoryItems != null && allHistoryItems.stream().anyMatch(V1SoldItem::isCollectedBySeller);
    }

    private void updateImportButton() {
        // Update the import button visibility and text based on the current mode.
        boolean isLocal = getEventService().isLocal();
        if (isLocal) {
            // Local mode: hide the button entirely (no web sync available)
            view.setImportButtonVisible(false);
            view.enableButton(BUTTON_IMPORT, false);
        } else {
            // Online mode: show and enable the button
            view.setImportButtonVisible(true);
            view.setImportButtonText(LocalizationManager.tr("button.update_web"));
            view.enableButton(BUTTON_IMPORT, true);
        }
    }

    private File[] selectFilesForImport() {
        // Open a file chooser dialog to select external JSONL files for import.
        String eventId = AppModeManager.getEventId();
        File initialDir = eventId == null
                ? LocalEventPaths.getEventsDir().toFile()
                : LocalEventPaths.getEventDir(eventId).toFile();
        
        return view.selectFilesForImport(initialDir);
    }

    private ImportResult importSoldItemsFromFile(File file) throws IOException {
        // Import sold items from the selected file, avoiding duplicates.
        List<V1SoldItem> importedItems = JsonlHelper.readItems(file.toPath());

        Set<String> existingItemIds = allHistoryItems.stream()
                .map(V1SoldItem::getItemId)
                .collect(Collectors.toSet());

        long added = 0;
        for (V1SoldItem item : importedItems) {
            if (!existingItemIds.contains(item.getItemId())) {
                allHistoryItems.add(item);
                added++;
            }
        }

        return new ImportResult(added, importedItems.size());
    }

    private record ImportResult(long added, long total) {}
}
