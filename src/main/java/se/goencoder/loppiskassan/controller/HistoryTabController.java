package se.goencoder.loppiskassan.controller;

import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.CreateSoldItems;
import se.goencoder.iloppis.model.ListSoldItemsResponse;
import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.records.FileHelper;
import se.goencoder.loppiskassan.records.FormatHelper;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.ui.HistoryPanelInterface;
import se.goencoder.loppiskassan.ui.Popup;
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
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static se.goencoder.iloppis.model.PaidFilter.PAID_FILTER_UNSPECIFIED;
import static se.goencoder.iloppis.model.PaymentMethodFilter.PAYMENT_METHOD_FILTER_UNSPECIFIED;
import static se.goencoder.loppiskassan.records.FileHelper.LOPPISKASSAN_CSV;
import static se.goencoder.loppiskassan.ui.Constants.*;

/**
 * Controls the history tab, handling the display, filtering, and management of sold items.
 */
public class HistoryTabController implements HistoryControllerInterface {
    private static final Logger logger = Logger.getLogger(HistoryTabController.class.getName());
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
            Popup.FATAL.showAndWait("Fel vid inläsning av fil", e.getMessage());
        }
    }

    @Override
    public void filterUpdated() {
        // Apply the current filters and update the view accordingly.
        List<SoldItem> filteredItems = applyFilters();

        view.updateHistoryTable(filteredItems);
        view.updateNoItemsLabel(String.valueOf(filteredItems.size()));
        view.updateSumLabel(String.valueOf(filteredItems.stream().mapToDouble(SoldItem::getPrice).sum()));

        boolean enablePayout = isPayoutEnabled(filteredItems);
        view.enableButton(BUTTON_PAY_OUT, enablePayout);

        boolean enableArchive = isArchiveEnabled();
        view.enableButton(BUTTON_ARCHIVE, enableArchive);

        updateImportButton();
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
        loadHistory();
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
            Popup.FATAL.showAndWait("Fel vid rensning av kassa", e);
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
            Popup.ERROR.showAndWait("Fel vid arkivering av poster", "Det går inte att arkivera poster som inte är utbetalda.");
            return;
        }

        if (!Popup.CONFIRM.showConfirmDialog(BUTTON_ARCHIVE, "Är du säker på att du vill arkivera de visade posterna?")) {
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
        saveHistoryToFile();
        filterUpdated();
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
            boolean allUploaded = uploadSoldItems();
            if (allUploaded) {
                downloadSoldItems();
            }
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
                                eventId,
                                PAID_FILTER_UNSPECIFIED.getValue(),
                                PAYMENT_METHOD_FILTER_UNSPECIFIED.getValue(),
                                null,
                                false,
                                500,
                                pageToken
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
        // Merge fetched items with existing items in history.
        fetchedItems.values().forEach(fetchedItem -> {
            allHistoryItems.stream()
                    .filter(item -> item.getItemId().equals(fetchedItem.getItemId()))
                    .findFirst()
                    .ifPresentOrElse(
                            existingItem -> {
                                existingItem.setCollectedBySellerTime(fetchedItem.getCollectedBySellerTime());
                                existingItem.setUploaded(fetchedItem.isUploaded());
                            },
                            () -> allHistoryItems.add(fetchedItem)
                    );
        });
    }

    private void saveHistoryToFile() {
        try {
            FileUtils.saveSoldItems(allHistoryItems);
        } catch (IOException e) {
            Popup.FATAL.showAndWait("Fel vid skrivning till fil", e.getMessage());
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
            Popup.FATAL.showAndWait("Fel vid arkivering av poster", e.getMessage());
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

    private boolean uploadSoldItems() {
        // Upload unsynchronized sold items to the web.
        List<SoldItem> notUploaded = allHistoryItems.stream()
                .filter(item -> !item.isUploaded())
                .collect(Collectors.toList());

        if (notUploaded.isEmpty()) return true;

        try {
            for (int i = 0; i < notUploaded.size(); i += 100) {
                int end = Math.min(i + 100, notUploaded.size());
                uploadBatch(notUploaded.subList(i, end));
            }
        } catch (Exception e) {
            Popup.ERROR.showAndWait("Fel vid uppladdning", e.getMessage());
            return false;
        }

        saveHistoryToFile();
        return true;
    }

    private void uploadBatch(List<SoldItem> batch) throws ApiException {
        // Upload a batch of sold items to the web service.
        CreateSoldItems createSoldItems = new CreateSoldItems();

        batch.forEach(item -> {
            se.goencoder.iloppis.model.SoldItem apiItem = SoldItemUtils.toApiSoldItem(item);
            apiItem.setSoldTime(OffsetDateTime.of(item.getSoldTime(), OffsetDateTime.now().getOffset()));
            createSoldItems.addItemsItem(apiItem);
        });

        ApiHelper.INSTANCE.getSoldItemsServiceApi().soldItemsServiceCreateSoldItems(
                ConfigurationStore.EVENT_ID_STR.get(), createSoldItems);
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

        Popup.INFORMATION.showAndWait("Import klar!", "Poster importerade: " + importedItems.size());
    }
}
