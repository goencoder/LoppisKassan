package se.goencoder.loppiskassan.controller;

import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.*;
import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.records.FileHelper;
import se.goencoder.loppiskassan.records.FormatHelper;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.ui.HistoryPanelInterface;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.ui.ProgressDialog;
import se.goencoder.loppiskassan.utils.FileUtils;
import se.goencoder.loppiskassan.utils.FilterUtils;
import se.goencoder.loppiskassan.utils.SoldItemUtils;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static se.goencoder.iloppis.model.PaidFilter.PAID_FILTER_UNSPECIFIED;
import static se.goencoder.iloppis.model.PaymentMethodFilter.PAYMENT_METHOD_FILTER_UNSPECIFIED;
import static se.goencoder.loppiskassan.records.FileHelper.LOPPISKASSAN_CSV;
import static se.goencoder.loppiskassan.ui.Constants.*;

/**
 * Controls the history tab, handling the display, filtering, and management of sold items.
 */
public class HistoryTabController implements HistoryControllerInterface {
    private static final HistoryTabController instance = new HistoryTabController();
    private HistoryPanelInterface view;
    private List<SoldItem> allHistoryItems;

    private HistoryTabController() {}

    public static HistoryTabController getInstance() {
        return instance;
    }

    @Override
    public void registerView(HistoryPanelInterface view) {
        this.view = view;
    }

    @Override
    public void loadHistory() {
        try {
            // Load the history from the local CSV file and populate the seller dropdown with distinct sellers.
            allHistoryItems = FormatHelper.toItems(FileHelper.readFromFile(LOPPISKASSAN_CSV), true);
            Set<String> distinctSellers = SoldItemUtils.getDistinctSellers(allHistoryItems);
            SwingUtilities.invokeLater(() -> view.updateSellerDropdown(distinctSellers));
        } catch (IOException e) {
            Popup.FATAL.showAndWait("Fel vid inläsning av kassafil: " + LOPPISKASSAN_CSV, e.getMessage());
        }
    }

    @Override
    public void filterUpdated() {
        updateImportButton();

        // Apply the current filters and update the view accordingly.
        List<SoldItem> filteredItems = applyFilters();

        view.updateHistoryTable(filteredItems);
        view.updateNoItemsLabel(String.valueOf(filteredItems.size()));
        view.updateSumLabel(String.valueOf(filteredItems.stream().mapToDouble(SoldItem::getPrice).sum()));

        boolean enablePayout = isPayoutEnabled(filteredItems);
        view.enableButton(BUTTON_PAY_OUT, enablePayout);

        boolean enableArchive = isArchiveEnabled();
        view.enableButton(BUTTON_ARCHIVE, enableArchive);

    }

    @Override
    public void buttonAction(String actionCommand) {
        switch (actionCommand) {
            case BUTTON_ERASE -> clearData();
            case BUTTON_IMPORT -> handleImportAction();
            case BUTTON_PAY_OUT -> payout();
            case BUTTON_COPY_TO_CLIPBOARD -> copyToClipboard();
            case BUTTON_ARCHIVE -> archiveFilteredItems();
            default -> throw new IllegalStateException("Unexpected action: " + actionCommand);
        }
    }

    private void downloadSoldItems() {
        // Retrieve all sold items from the web, merge with local items, and save them to the local file.
        String eventId = ConfigurationStore.EVENT_ID_STR.get();
        Map<String, SoldItem> fetchedItems = fetchItemsFromWeb(eventId);
        mergeFetchedItems(fetchedItems);
        saveHistoryToFile();
        updateDistinctSellers();
        filterUpdated();
    }

    private void clearData() {
        // Clear all local data after user confirmation.
        try {
            if (Popup.CONFIRM.showConfirmDialog(BUTTON_ERASE, "Är du säker på att du vill rensa kassan?")) {
                FileHelper.createBackupFile();
                allHistoryItems.clear();
                filterUpdated();
            }
        } catch (IOException e) {
            Popup.FATAL.showAndWait(
                    "Fel vid rensning av kassafil: " + LOPPISKASSAN_CSV,
                    e.getMessage());
        }
    }

    private void importData() {
        // Import sold items from an external file selected by the user.
        File file = selectFileForImport();
        if (file == null) return;

        try {
            importSoldItemsFromFile(file);
        } catch (Exception e) {
            Popup.ERROR.showAndWait("Importering misslyckades", e.getMessage());
        }
    }

    private void archiveFilteredItems() {
        // Archive filtered items to a CSV file and remove them from the history list.
        List<SoldItem> filteredItems = applyFilters();

        if (filteredItems.stream().anyMatch(item -> !item.isCollectedBySeller())) {
            Popup.ERROR.showAndWait(
                    "Fel vid arkivering av poster",
                    "Det går inte att arkivera poster som inte är utbetalda.");
            return;
        }

        if (!Popup.CONFIRM.showConfirmDialog(BUTTON_ARCHIVE,
                "Är du säker på att du vill arkivera de visade posterna?")) {
            return;
        }

        archiveItemsToFile(filteredItems);
        removeFilteredItems(filteredItems);
        saveHistoryToFile();
        // Refresh UI components
        updateDistinctSellers();
        filterUpdated();
    }

    private void updateDistinctSellers() {
        Set<String> distinctSellers = SoldItemUtils.getDistinctSellers(allHistoryItems);
        SwingUtilities.invokeLater(() -> view.updateSellerDropdown(distinctSellers));
    }

    private void payout() {
        // Mark filtered items as paid out and update the history.
        List<SoldItem> filteredItems = applyFilters();
        LocalDateTime now = LocalDateTime.now();

        filteredItems.forEach(item -> item.setCollectedBySellerTime(now));
        try {
            payoutWeb();
            saveHistoryToFile();
            filterUpdated();
        } catch (ApiException e) {
            Popup.ERROR.showAndWait("Fel vid utbetalning", e.getMessage());
        }
    }

    private void payoutWeb() throws ApiException {
        // Create a payout request with the current filter settings
        SoldItemsServicePayoutBody payoutBody = new SoldItemsServicePayoutBody();

        try {
            int seller = Integer.parseInt(view.getSellerFilter());
            payoutBody.setSeller(seller);
        } catch (NumberFormatException e) {
            // Do nothing if seller filter isn't a valid number
        }

        try {
            PaymentMethodFilter paymentMethodFilter = PaymentMethodFilter.fromValue(view.getPaymentMethodFilter());
            payoutBody.setPaymentMethodFilter(paymentMethodFilter);
        } catch (IllegalArgumentException e) {
            // Do nothing if payment method filter isn't valid
        }

        payoutBody.setUntilTimestamp(OffsetDateTime.now());

        // Call the API to process the payout
        ApiHelper.INSTANCE.getSoldItemsServiceApi().soldItemsServicePayout(
                ConfigurationStore.EVENT_ID_STR.get(),
                payoutBody
        );
    }

    private void copyToClipboard() {
        // Copy a summary of filtered items to the system clipboard.
        List<SoldItem> filteredItems = applyFilters();
        String summary = generateSummary(filteredItems);

        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(summary), null);
    }

    private List<SoldItem> applyFilters() {
        // Apply the current filters to the history items.
        return FilterUtils.applyFilters(
                allHistoryItems,
                view.getPaidFilter(),
                view.getSellerFilter(),
                view.getPaymentMethodFilter()
        );
    }

    private void handleImportAction() {
        if (ConfigurationStore.OFFLINE_EVENT_BOOL.getBooleanValueOrDefault(false)) {
            importData();
        } else {

            ProgressDialog.runTask(
                    view.getComponent(),
                    "Uppdaterar Poster",
                    "Synkroniserar poster med iLoppis...",
                    () -> {

                        uploadSoldItems();
                        downloadSoldItems();

                        return null;
                    },
                    _ -> {

                    },
                    e -> Popup.ERROR.showAndWait(
                            "Nätverksfel",
                            "Kunde inte hämta poster från iLoppis. Kontrollera din internetanslutning.")
            );
        }
    }

    private Map<String, SoldItem> fetchItemsFromWeb(String eventId) {
        // Fetch items from the web service.
        Map<String, SoldItem> fetchedItems = new HashMap<>();
        try {
            String pageToken = "";
            boolean fetchedAll = false;

            while (!fetchedAll) {
                ListSoldItemsResponse result = ApiHelper.INSTANCE.getSoldItemsServiceApi()
                        .soldItemsServiceListSoldItems(
                                eventId,                              // eventId
                                null,                                 // purchaseId
                                PAID_FILTER_UNSPECIFIED.getValue(),   // paidFilter
                                PAYMENT_METHOD_FILTER_UNSPECIFIED.getValue(), // paymentMethodFilter
                                null,                                 // seller
                                Boolean.FALSE,                        // includeArchived
                                Integer.valueOf(500),                 // pageSize
                                pageToken,                           // nextPageToken
                                "",                                  // prevPageToken
                                Boolean.FALSE                        // includeAggregates
                        );

                result.getItems().forEach(item -> {
                    SoldItem soldItem = SoldItemUtils.fromApiSoldItem(item, true);
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

    private void mergeFetchedItems(Map<String, SoldItem> fetchedItems) {
        // Track items that have been added to prevent duplicates
        Set<String> processedItems = new HashSet<>();

        // First pass: Update existing items
        allHistoryItems.forEach(existingItem -> {
            SoldItem fetchedItem = fetchedItems.get(existingItem.getItemId());
            if (fetchedItem != null) {
                // Update existing item
                existingItem.setCollectedBySellerTime(fetchedItem.getCollectedBySellerTime());
                existingItem.setUploaded(fetchedItem.isUploaded());
                processedItems.add(existingItem.getItemId());
            }
        });

        // Second pass: Add new items, but check for potential duplicates
        for (SoldItem fetchedItem : fetchedItems.values()) {
            if (processedItems.contains(fetchedItem.getItemId())) {
                // Already processed this item
                continue;
            }

            // Check for potential duplicate by matching seller, price, and close timestamp
            boolean isDuplicate = allHistoryItems.stream().anyMatch(existingItem ->
                    existingItem.getSeller() == fetchedItem.getSeller() &&
                    existingItem.getPrice() == fetchedItem.getPrice() &&
                    isSameTimeApproximately(existingItem.getSoldTime(), fetchedItem.getSoldTime(), 60) // 60 seconds tolerance
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
        try {
            FileUtils.saveSoldItems(allHistoryItems);
        } catch (IOException e) {
            Popup.FATAL.showAndWait(
                    "Fel vid skrivning till kassafil: " + LOPPISKASSAN_CSV,
                    e.getMessage());
        }
    }

    private void archiveItemsToFile(List<SoldItem> filteredItems) {
        // Save filtered items to an archive file with a timestamped filename.
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yy-MM-dd_HH-mm-ss"));
        String fileName = "arkiverade_" + timestamp + ".csv";

        String comment = "# Säljare: " + (view.getSellerFilter() == null ? "Alla" : view.getSellerFilter()) +
                ", Betalningsmetod: " + (view.getPaymentMethodFilter() == null ? "Alla" : view.getPaymentMethodFilter());

        try {
            FileHelper.saveToFile(fileName, comment, FormatHelper.toCVS(filteredItems));
        } catch (IOException e) {
            Popup.FATAL.showAndWait(
                    "Fel vid arkivering av poster till fil: " + fileName,
                    e.getMessage());
        }
    }

    private void removeFilteredItems(List<SoldItem> filteredItems) {
        // Remove archived items from the history list.
        Set<String> filteredItemIds = filteredItems.stream()
                .map(SoldItem::getItemId)
                .collect(Collectors.toSet());

        allHistoryItems.removeIf(item -> filteredItemIds.contains(item.getItemId()));
    }

    private String generateSummary(List<SoldItem> filteredItems) {
        // Generate a summary string for filtered items.
        int totalItems = filteredItems.size();
        int totalSum = filteredItems.stream().mapToInt(SoldItem::getPrice).sum();
        int provision = (int) (0.1 * totalSum);

        StringBuilder summary = new StringBuilder("Säljredovisning:\n");
        summary.append(totalItems).append(" varor sålda för totalt ").append(totalSum).append(" SEK.\n");
        summary.append("Provision: ").append(provision).append(" SEK.\n");

        filteredItems.forEach(item -> summary.append(item.toString()).append("\n"));

        return summary.toString();
    }

    // TODO: This is not working, if offline with items to upload, message says we failed download - but all items uploaded which is not correct
    // TODO: If we have incorret items in the list not uploaded (eg incorrect seller id) and we try to sync - we do not show popup telling that some items could not be uploaded
    private boolean uploadSoldItems() {
        // 1) Hitta alla poster som inte redan är uppladdade
        List<SoldItem> notUploaded = allHistoryItems.stream()
                .filter(item -> !item.isUploaded())
                .collect(Collectors.toList());

        if (notUploaded.isEmpty()) {
            // Inget att ladda upp => returnera true, allt "ok"
            return true;
        }

        List<RejectedItem> allRejected = new ArrayList<>();
        boolean networkError = false; // För att särskilja nätverksfel

        // 2) Kör i sub‐batchar om 100 poster
        for (int i = 0; i < notUploaded.size(); i += 100) {
            int end = Math.min(i + 100, notUploaded.size());
            List<SoldItem> subBatch = notUploaded.subList(i, end);

            try {
                // 2.1) Skicka upp subBatch
                List<RejectedItem> rejectedItems = uploadBatch(subBatch);
                // 2.2) Spara eventuella avvisade
                allRejected.addAll(rejectedItems);

            } catch (ApiException e) {
                // 3) Skilj på nätverksfel och "riktiga" API-fel
                if (ApiHelper.isLikelyNetworkError(e)) {
                    // Avbryt uppladdning helt
                    networkError = true;
                    break;
                } else {
                    Popup.ERROR.showAndWait("Fel vid uppladdning till web", e.getMessage());
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
                    "Nätverksfel",
                    "Kunde inte nå servern för att ladda upp fler poster.\n"+
                            "Vissa batchar kan ha laddats upp innan felet uppstod."
            );
            return false;
        }

        // 6) Visa om vi fick avvisade poster p.g.a. API-fel i subBatch (typ fel data)
        if (!allRejected.isEmpty()) {
            StringBuilder msg = new StringBuilder("Följande poster avvisades vid uppladdning:\n");
            for (RejectedItem rejectedItem : allRejected) {

                msg.append("- ")
                        .append(rejectedItem.getItem().getItemId())
                        .append(", säljare: ")
                        .append(rejectedItem.getItem().getSeller())
                        .append(", pris: ")
                        .append(rejectedItem.getItem().getPrice())
                        .append(", betalmetod: ")
                        .append(rejectedItem.getItem().getPaymentMethod())
                        .append(", orsak: ")
                        .append(rejectedItem.getReason())
                        .append("\n");
            }
            Popup.ERROR.showAndWait("Uppladdning misslyckades för vissa varor", msg.toString());
            // Returnera false => vi vet att allt inte gick igenom
            return false;
        }

        // 7) Kommer vi hit har vi varken nätverksfel eller avvisade poster => allt gick bra
        return true;
    }

    private List<RejectedItem> uploadBatch(List<SoldItem> batch) throws ApiException {
        // 1. Build a SoldItemsServiceCreateSoldItemsBody object for all items in the batch
        SoldItemsServiceCreateSoldItemsBody requestBody = new SoldItemsServiceCreateSoldItemsBody();

        for (SoldItem localItem : batch) {
            se.goencoder.iloppis.model.SoldItem apiItem = SoldItemUtils.toApiSoldItem(localItem);
            // Set timezone before adding to the request
            apiItem.setSoldTime(OffsetDateTime.of(
                    localItem.getSoldTime(),
                    OffsetDateTime.now().getOffset()
            ));
            requestBody.addItemsItem(apiItem);
        }

        // 2. Call the API and receive the result
        CreateSoldItemsResponse response =
                ApiHelper.INSTANCE.getSoldItemsServiceApi()
                        .soldItemsServiceCreateSoldItems(
                                ConfigurationStore.EVENT_ID_STR.get(),
                                requestBody);

        // 3. Create a map to easily find the right object in the batch
        Map<String, SoldItem> localMap = batch.stream()
                .collect(Collectors.toMap(SoldItem::getItemId, Function.identity()));

        // 4. Handle acceptedItems:
        //    Mark them as uploaded (isUploaded = true) in the local objects
        if (response.getAcceptedItems() != null) {
            for (se.goencoder.iloppis.model.SoldItem accepted : response.getAcceptedItems()) {
                SoldItem localItem = localMap.get(accepted.getItemId());
                if (localItem != null) {
                    localItem.setUploaded(true);
                    // Update more fields if needed, e.g., collectedBySellerTime etc.
                }
            }
        }

        // 5. Handle rejectedItems
        return response.getRejectedItems();
    }

    private boolean isPayoutEnabled(List<SoldItem> filteredItems) {
        // Enable payout if there are unpaid items for the selected seller.
        return filteredItems.stream().anyMatch(item -> !item.isCollectedBySeller());
    }

    private boolean isArchiveEnabled() {
        // Enable archive if the "Paid" filter is set to "Yes."
        return "Ja".equals(view.getPaidFilter());
    }

    private void updateImportButton() {
        // Update the import button text and enable it based on the current mode.
        boolean isOffline = ConfigurationStore.OFFLINE_EVENT_BOOL.getBooleanValueOrDefault(false);
        if (isOffline) {
            view.setImportButtonText("Importera kassa");
        } else {
            view.setImportButtonText("Uppdatera med Web");
        }
        view.enableButton(BUTTON_IMPORT, true);
    }

    private File selectFileForImport() {
        // Open a file chooser dialog to select an external file for import.
        JFileChooser fileChooser = new JFileChooser(FileHelper.getRecordFilePath(LOPPISKASSAN_CSV).toFile());
        fileChooser.setDialogTitle("Öppna annan kassa-fil");
        fileChooser.setFileFilter(new FileNameExtensionFilter("CSV Files", "csv"));

        int result = fileChooser.showOpenDialog(null);
        return result == JFileChooser.APPROVE_OPTION ? fileChooser.getSelectedFile() : null;
    }

    private void importSoldItemsFromFile(File file) throws IOException {
        // Import sold items from the selected file, avoiding duplicates.
        List<SoldItem> importedItems = FormatHelper.toItems(FileHelper.readFromFile(file.toPath()), true);

        Set<String> existingItemIds = allHistoryItems.stream()
                .map(SoldItem::getItemId)
                .collect(Collectors.toSet());

        importedItems.stream()
                .filter(item -> !existingItemIds.contains(item.getItemId()))
                .forEach(allHistoryItems::add);

        saveHistoryToFile();
        filterUpdated();

        Popup.INFORMATION.showAndWait(
                "Import klar!",
                "Poster importerade: " + importedItems.size());
    }
}
