package se.goencoder.loppiskassan.controller;

import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.CreateSoldItems;
import se.goencoder.iloppis.model.CreateSoldItemsResponse;
import se.goencoder.iloppis.model.ListSoldItemsResponse;
import se.goencoder.loppiskassan.Filter;
import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.records.FileHelper;
import se.goencoder.loppiskassan.records.FormatHelper;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.ui.HistoryPanelInterface;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.utils.SoldItemUtils;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static se.goencoder.iloppis.model.PaidFilter.PAID_FILTER_UNSPECIFIED;
import static se.goencoder.iloppis.model.PaymentMethodFilter.PAYMENT_METHOD_FILTER_UNSPECIFIED;
import static se.goencoder.loppiskassan.records.FileHelper.LOPPISKASSAN_CSV;
import static se.goencoder.loppiskassan.ui.Constants.*;

public class HistoryTabController implements HistoryControllerInterface {
    private static final Logger logger = Logger.getLogger(HistoryTabController.class.getName());
    private static final HistoryTabController instance = new HistoryTabController();
    private HistoryPanelInterface view;
    private List<SoldItem> allHistoryItems;

    private HistoryTabController() {
    }

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
            allHistoryItems = FormatHelper.toItems(FileHelper.readFromFile(LOPPISKASSAN_CSV), true);
            Set<String> distinctSellers = SoldItemUtils.getDistinctSellers(allHistoryItems);
            SwingUtilities.invokeLater(() -> view.updateSellerDropdown(distinctSellers));
        } catch (IOException e) {
            Popup.FATAL.showAndWait("Fel vid inläsning av fil", e.getMessage());
        }
    }

    @Override
    public void filterUpdated() {
        String sellerFilter = view.getSellerFilter();
        String paidFilter = view.getPaidFilter();
        List<SoldItem> filteredItems = applyFilters();

        // Update the table
        view.updateHistoryTable(filteredItems);
        view.updateNoItemsLabel(Integer.toString(filteredItems.size()));
        view.updateSumLabel(Double.toString(filteredItems.stream().mapToDouble(SoldItem::getPrice).sum()));

        // Enable or disable "Betala ut" button
        boolean enablePayout = sellerFilter != null && !sellerFilter.equals("Alla")
                && filteredItems.stream().anyMatch(item -> !item.isCollectedBySeller());
        view.enableButton(BUTTON_PAY_OUT, enablePayout);

        // Enable or disable "Arkivera" button
        boolean enableArchive = paidFilter != null && paidFilter.equals("Ja");
        view.enableButton(BUTTON_ARCHIVE, enableArchive);

        boolean isOffline = ConfigurationStore.OFFLINE_EVENT_BOOL.getBooleanValueOrDefault(false);
        if (isOffline) {
            // If offline: text => "Importera kassa"
            view.setImportButtonText("Importera kassa");
            view.enableButton(BUTTON_IMPORT, true);
        } else {
            // If online: check if ALL items are uploaded
            view.setImportButtonText("Uppdatera med Web");
            view.enableButton(BUTTON_IMPORT, true);
        }
    }

    @Override
    public void buttonAction(String actionCommand) {
        switch (actionCommand) {
            case BUTTON_ERASE:
                clearData();
                break;
            case BUTTON_IMPORT:
                // If offline => old importData()
                if (ConfigurationStore.OFFLINE_EVENT_BOOL.getBooleanValueOrDefault(false)) {
                    importData();
                } else {
                    // If online => new updateWithWeb
                    boolean allUploaded = uploadSoldItems();
                    if (allUploaded) {
                        downloadSoldItems();
                    }

                }
                break;
            case BUTTON_PAY_OUT:
                payout();
                break;
            case BUTTON_COPY_TO_CLIPBOARD:
                copyToClipboard();
                break;
            case BUTTON_ARCHIVE:
                archiveFilteredItems();
                break;
            default:
                throw new IllegalStateException("Unexpected action: " + actionCommand);
        }
    }

    private void downloadSoldItems() {
        // Download all sold items from the web, merge with local items, and save to file
        String eventId = ConfigurationStore.EVENT_ID_STR.get();

        Map<String, SoldItem> fetchedItems = new HashMap<>();
        try {
            boolean fetchedAll = false;
            String pageToken = "";
            while (!fetchedAll) {
                ListSoldItemsResponse result = ApiHelper.INSTANCE.getSoldItemsServiceApi().soldItemsServiceListSoldItems(
                        eventId,
                        PAID_FILTER_UNSPECIFIED.getValue(),
                        PAYMENT_METHOD_FILTER_UNSPECIFIED.getValue(),
                        null, false, 500, pageToken);
                for (se.goencoder.iloppis.model.SoldItem item : result.getItems()) {
                    SoldItem soldItem = FormatHelper.apiSoldItemToSoldItem(item, true);
                    fetchedItems.put(soldItem.getItemId(), soldItem);
                }
                if (result.getNextPageToken() == null || result.getNextPageToken().isEmpty()) {
                    fetchedAll = true;
                } else {
                    pageToken = result.getNextPageToken();
                }
                logger.info("Downloaded " + result.getItems().size() + " items from the web.");
            }
        } catch (ApiException e) {
            throw new RuntimeException(e);
        }
        // merge allHistoryItems with fetchedItems so that new items are added and existing items are updated
        for (SoldItem fetchedItem : fetchedItems.values()) {
            if (allHistoryItems.stream().noneMatch(item -> item.getItemId().equals(fetchedItem.getItemId()))) {
                allHistoryItems.add(fetchedItem);
            } else {
                SoldItem existingItem = allHistoryItems.stream()
                        .filter(item -> item.getItemId().equals(fetchedItem.getItemId()))
                        .findFirst()
                        .orElseThrow();
                existingItem.setCollectedBySellerTime(fetchedItem.getCollectedBySellerTime());
                existingItem.setUploaded(fetchedItem.isUploaded());
            }
        }
        try {
            FileHelper.createBackupFile();
            FileHelper.saveToFile(LOPPISKASSAN_CSV, "", FormatHelper.toCVS(allHistoryItems));
            loadHistory();
        } catch (IOException e) {
            Popup.FATAL.showAndWait("Fel vid skrivning till fil", e.getMessage());
        }
    }

    private boolean uploadSoldItems() {
        List<SoldItem> unuploaded = allHistoryItems.stream()
                .filter(item -> !item.isUploaded())
                .collect(Collectors.toList());
        boolean allUploaded = true;

        if (unuploaded.isEmpty()) {
            return true;
        }

        try {
            int index = 0;
            while (index < unuploaded.size()) {
                int toIndex = Math.min(index + 100, unuploaded.size());
                List<SoldItem> batch = unuploaded.subList(index, toIndex);

                // Upload this batch
                allUploaded = allUploaded && uploadBatch(batch);

                // Mark them as uploaded
                batch.forEach(item -> item.setUploaded(true));

                index = toIndex;
            }

            // Save updated list to file
            FileHelper.createBackupFile();
            FileHelper.saveToFile(LOPPISKASSAN_CSV, "", FormatHelper.toCVS(allHistoryItems));

            // Refresh filters so the UI updates (button state, table, etc.)
            filterUpdated();

            Popup.INFORMATION.showAndWait("Klart!", "Uppladdning av " + unuploaded.size() + " varor slutförd.");
        } catch (Exception e) {
            Popup.ERROR.showAndWait("Fel vid uppladdning", e.getMessage());
            allUploaded = false;
        }
        return allUploaded;
    }

    /**
     * Helper to upload a single batch to the web.
     */
    private boolean uploadBatch(List<SoldItem> batch) throws ApiException {
        CreateSoldItems createSoldItems = new CreateSoldItems();
        for (SoldItem item : batch) {
            se.goencoder.iloppis.model.SoldItem apiItem = SoldItemUtils.toApiSoldItem(item);

            OffsetDateTime soldTime = OffsetDateTime.of(
                    item.getSoldTime(),
                    OffsetDateTime.now().getOffset()
            );
            apiItem.setSoldTime(soldTime);

            createSoldItems.addItemsItem(apiItem);
        }

        // Send this batch to the API
        CreateSoldItemsResponse result = ApiHelper.INSTANCE.getSoldItemsServiceApi().soldItemsServiceCreateSoldItems(
                ConfigurationStore.EVENT_ID_STR.get(),
                createSoldItems
        );
        return result.getRejectedItems().isEmpty();
    }


    private void clearData() {
        try {
            if (Popup.CONFIRM.showConfirmDialog(BUTTON_ERASE, "Är du säker på att du vill rensa kassan?")) {
                FileHelper.createBackupFile();
                allHistoryItems.clear();
                filterUpdated();
                FileHelper.createBackupFile();
                allHistoryItems.clear();
                filterUpdated();
            }
        } catch (IOException e) {
            Popup.FATAL.showAndWait("Fel vid rensning av kassa", e);
        }
    }

    private void importData() {
        Path defaultPath = FileHelper.getRecordFilePath(LOPPISKASSAN_CSV);
        JFileChooser fileChooser = new JFileChooser(defaultPath.toFile());
        fileChooser.setDialogTitle("Öppna annan kassa-fil");
        // Optional: Filter for specific file types, e.g., CSV files
        FileNameExtensionFilter filter = new FileNameExtensionFilter("CSV Files", "csv");
        fileChooser.setFileFilter(filter);

        int result = fileChooser.showOpenDialog(null); // Pass your JFrame here if needed
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            // create a set of item ids for the allHistoryItems list
            Set<String> allItemIds = allHistoryItems.stream()
                    .map(SoldItem::getItemId)
                    .collect(Collectors.toSet());
            try {
                List<SoldItem> importedItems = FormatHelper.toItems(FileHelper.readFromFile(Paths.get(file.getAbsolutePath())), true);
                int numberOfImportedItems = 0;
                for (SoldItem item : importedItems) {
                    if (!allItemIds.contains(item.getItemId())) {
                        allHistoryItems.add(item);
                        numberOfImportedItems++;
                    }
                }
                FileHelper.createBackupFile();
                FileHelper.saveToFile(LOPPISKASSAN_CSV, "", FormatHelper.toCVS(allHistoryItems));
                // Assuming populateHistoryTable() and updateHistoryLabels() are methods that refresh your UI
                view.clearView();
                filterUpdated();

                // Information dialog
                JOptionPane.showMessageDialog(null,
                        "Importerade " + numberOfImportedItems + " av " + importedItems.size() + " poster",
                        "Import klar!",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                // Error dialog
                JOptionPane.showMessageDialog(null,
                        "Importering misslyckades: " + e.getMessage(),
                        "Importfel",
                        JOptionPane.ERROR_MESSAGE);
                logger.log(java.util.logging.Level.SEVERE, "Error importing file", e);
            }
        }
    }

    private void archiveFilteredItems() {


        // get filtered items, save to a file named "arkiverade_<YY-MM-DD:HH-MM-SS>.csv"
        // The first row in the file is a comment with the applied filters, eg:
        // "# Säljare: 12, Betalningsmetod: Swish, Dölj utbetalda poster: Ja"
        // The following rows are the items in CSV format
        // Next, remove the filtered items from the allHistoryItems list and save that list to the main file
        // Finally, update the view with the new list of items
        List<SoldItem> filteredItems = applyFilters();
        // if filtered Items contains items that has not been paid out, show an error popup and return
        if (filteredItems.stream().anyMatch(item -> !item.isCollectedBySeller())) {
            Popup.ERROR.showAndWait("Fel vid arkivering av poster", "Det går inte att arkivera poster som inte är utbetalda.");
            return;
        }
        // Show a confirmation dialog, and proceed only if the user confirms
        if (!Popup.CONFIRM.showConfirmDialog(BUTTON_ARCHIVE, "Är du säker på att du vill arkivera de visade posterna?")) {
            return;
        }
        String csv = FormatHelper.toCVS(filteredItems);
        String fileName = "arkiverade_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yy-MM-dd_HH-mm-ss")) + ".csv";
        String comment = "# Säljare: " + (view.getSellerFilter() == null || view.getSellerFilter().isEmpty() ? "Alla" : view.getSellerFilter())
                + ", Betalningsmetod: " + (view.getPaymentMethodFilter() == null || view.getPaymentMethodFilter().isEmpty() ? "Alla" : view.getPaymentMethodFilter());
        try {
            FileHelper.saveToFile(fileName, comment, csv);
            logger.info("Allitems are " + allHistoryItems.size() + " and filtered items are " + filteredItems.size());
            // Create a set of item IDs for the filtered items
            Set<String> filteredItemIds = filteredItems.stream()
                    .map(SoldItem::getItemId)
                    .collect(Collectors.toSet());
            // Remove the filtered items from the allHistoryItems list by filtering out items with IDs in the set
            // Loop from end and remove as we go to avoid ConcurrentModificationException
            for (int i = allHistoryItems.size() - 1; i >= 0; i--) {
                if (filteredItemIds.contains(allHistoryItems.get(i).getItemId())) {
                    allHistoryItems.remove(i);
                }
            }
            logger.info("Allitems are now " + allHistoryItems.size() + " and filtered items are " + filteredItems.size());
            FileHelper.createBackupFile();
            FileHelper.saveToFile(LOPPISKASSAN_CSV, "", FormatHelper.toCVS(allHistoryItems));
            filterUpdated();
        } catch (IOException e) {
            Popup.FATAL.showAndWait("Fel vid arkivering av poster", e.getMessage());
        }

    }

    private void payout() {
        // Get filtered items
        List<SoldItem> filteredItems = applyFilters();
        // Iterate over filtered items and mark them as collected by seller (Local date now)
        LocalDateTime now = LocalDateTime.now();
        // Update allHistoryItems with changes made to filteredItems
        Set<String> filteredItemIds = filteredItems.stream()
                .map(SoldItem::getItemId)
                .collect(Collectors.toSet());

        allHistoryItems.forEach(historyItem -> {
            if (filteredItemIds.contains(historyItem.getItemId())) {
                historyItem.setCollectedBySellerTime(now);
            }
        });

        String csv = FormatHelper.toCVS(allHistoryItems);
        // Write CSV string to file
        try {
            FileHelper.createBackupFile();
            FileHelper.saveToFile(LOPPISKASSAN_CSV, "", csv);
        } catch (IOException e) {
            Popup.FATAL.showAndWait("Fel vid skrivning till fil", e.getMessage());
        }
        filterUpdated();
    }


    private void copyToClipboard() {
        // Assuming allHistoryItems is the complete list, and we apply filters similar to your filterUpdated method
        List<SoldItem> filteredItems = applyFilters();
        final String NL = System.lineSeparator();
        int numberOfItems = filteredItems.size();
        int sum = filteredItems.stream().mapToInt(SoldItem::getPrice).sum();
        int provision = (int) (0.1 * sum);
        StringBuilder itemsDetailed = new StringBuilder();
        int index = 1;
        for (SoldItem item : filteredItems) {
            itemsDetailed.append(index++).append(".\t").append(item.getPrice()).append(" SEK ")
                    .append(item.isCollectedBySeller() ? "Utbetalt" : "Ej utbetalt").append(NL);
        }

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        String formattedDateTime = now.format(formatter);

        StringBuilder header = new StringBuilder();
        header.append("Säljredovisning för ")
                .append((view.getSellerFilter() == null || "Alla".equals(view.getSellerFilter())) ? "alla säljare" : "säljare " + view.getSellerFilter())
                .append(".")
                .append(NL)
                .append(numberOfItems)
                .append(" sålda varor ")
                .append("för totalt ")
                .append(sum)
                .append(" SEK.")
                .append(NL)
                .append("Redovisningen omfattar följande betalningsmetoder: ")
                .append(view.getPaymentMethodFilter() != null ? view.getPaymentMethodFilter() : "Alla")
                .append("\ngenomförda innan ").append(formattedDateTime)
                .append('.');
        if (view.getSellerFilter() != null && !"Alla".equals(view.getSellerFilter())) {
            header.append(NL).append("Provision: ").append(provision)
                    .append(" Utbetalas säljare: ").append((sum - provision));
        }

        header.append(NL).append(NL).append(itemsDetailed);

        // Copy the string to the clipboard
        StringSelection stringSelection = new StringSelection(header.toString());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(stringSelection, null);
    }

    private List<SoldItem> applyFilters() {
        String paidFilter = view.getPaidFilter(); // This will be "Ja", "Nej", or "Alla"
        String sellerFilter = view.getSellerFilter();
        String paymentMethodFilter = view.getPaymentMethodFilter();
        return allHistoryItems.stream()
                // Apply the paid filter based on the selected value
                .filter(item -> switch (paidFilter) {
                    case "Ja" -> item.isCollectedBySeller();
                    case "Nej" -> !item.isCollectedBySeller();
                    default -> true; // No filtering on paid status
                })
                // Apply the seller filter if a specific seller is selected (not null and not "Alla")
                .filter(item -> sellerFilter == null || "Alla".equals(sellerFilter) || Filter.getFilterFunc(Filter.SELLER, sellerFilter).test(item))
                // Apply the payment method filter if a specific payment method is selected (not null and not "Alla")
                .filter(item -> paymentMethodFilter == null || "Alla".equals(paymentMethodFilter) || Filter.getFilterFunc(Filter.PAYMENT_METHOD, paymentMethodFilter).test(item))
                .collect(Collectors.toList());
    }

}
