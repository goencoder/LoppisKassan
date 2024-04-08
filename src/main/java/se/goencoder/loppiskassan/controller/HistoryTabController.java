package se.goencoder.loppiskassan.controller;

import se.goencoder.loppiskassan.Filter;
import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.records.FileHelper;
import se.goencoder.loppiskassan.records.FormatHelper;
import se.goencoder.loppiskassan.ui.HistoryPanelInterface;
import se.goencoder.loppiskassan.ui.Popup;

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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static se.goencoder.loppiskassan.records.FileHelper.LOPPISKASSAN_CSV;
import static se.goencoder.loppiskassan.records.FileHelper.getRecordFilePath;

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
            allHistoryItems = FormatHelper.toItems(FileHelper.readFromFile(LOPPISKASSAN_CSV), true);
            Set<String> distinctSellers = getDistinctSellers();
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
        view.updateHistoryTable(filteredItems);
        // convert filtered items size to string and call updateNoItemsLabel
        view.updateNoItemsLabel(Integer.toString(filteredItems.size()));
        // Aggregate the total sum of the filtered items and call updateTotalSumLabel
        view.updateSumLabel(Double.toString(filteredItems.stream().mapToDouble(SoldItem::getPrice).sum()));
        // Determine if the "Betala ut" button should be enabled
        boolean enablePayout = sellerFilter != null && !sellerFilter.equals("Alla") && filteredItems.stream().anyMatch(item -> !item.isCollectedBySeller());
        view.enableButton("Betala ut", enablePayout);
        boolean enableArchive = paidFilter != null && paidFilter.equals("Ja");
        view.enableButton("Arkivera visade poster", enableArchive);
    }

    @Override
    public void buttonAction(String actionCommand) {
        switch (actionCommand) {
            case "Rensa kassan":
                clearData();
                break;
            case "Importera kassa":
                importData();
                break;
            case "Betala ut":
                payout();
                break;
            case "Kopiera till urklipp":
                copyToClipboard();
                break;
            case "Arkivera visade poster":
                archiveFilteredItems();
                break;
            default:
                throw new IllegalStateException("Unexpected action: " + actionCommand);
        }
    }

    private void clearData() {
        try {
            if (Popup.CONFIRM.showConfirmDialog("Rensa kassan", "Är du säker på att du vill rensa kassan?")) {
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
                e.printStackTrace();
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
        if (!Popup.CONFIRM.showConfirmDialog("Arkivera visade poster", "Är du säker på att du vill arkivera de visade posterna?")) {
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

    private Set<String> getDistinctSellers() {
        return allHistoryItems.stream()
                .map(item -> String.valueOf(item.getSeller()))
                .collect(Collectors.toSet());
    }

    private List<SoldItem> applyFilters() {
        String paidFilter = view.getPaidFilter(); // This will be "Ja", "Nej", or "Alla"
        String sellerFilter = view.getSellerFilter();
        String paymentMethodFilter = view.getPaymentMethodFilter();
        return allHistoryItems.stream()
                // Apply the paid filter based on the selected value
                .filter(item -> {
                    switch (paidFilter) {
                        case "Ja":
                            return item.isCollectedBySeller();
                        case "Nej":
                            return !item.isCollectedBySeller();
                        case "Alla":
                        default:
                            return true; // No filtering on paid status
                    }
                })
                // Apply the seller filter if a specific seller is selected (not null and not "Alla")
                .filter(item -> sellerFilter == null || "Alla".equals(sellerFilter) || Filter.getFilterFunc(Filter.SELLER, sellerFilter).test(item))
                // Apply the payment method filter if a specific payment method is selected (not null and not "Alla")
                .filter(item -> paymentMethodFilter == null || "Alla".equals(paymentMethodFilter) || Filter.getFilterFunc(Filter.PAYMENT_METHOD, paymentMethodFilter).test(item))
                .collect(Collectors.toList());
    }

}
