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
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
            allHistoryItems = FormatHelper.toItems(FileHelper.readFromFile(), true);
            Set<String> distinctSellers = getDistinctSellers();
            SwingUtilities.invokeLater(() -> view.updateSellerDropdown(distinctSellers));
        } catch (IOException e) {
            Popup.FATAL.showAndWait("Fel vid inläsning av fil", e.getMessage());
        }
    }

    @Override
    public void filterUpdated() {
        String sellerFilter = view.getSellerFilter();
        List<SoldItem> filteredItems = applyFilters();
        view.updateHistoryTable(filteredItems);
        // convert filtered items size to string and call updateNoItemsLabel
        view.updateNoItemsLabel(Integer.toString(filteredItems.size()));
        // Aggregate the total sum of the filtered items and call updateTotalSumLabel
        view.updateSumLabel(Double.toString(filteredItems.stream().mapToDouble(SoldItem::getPrice).sum()));
        // Determine if the "Betala ut" button should be enabled
        boolean enablePayout = sellerFilter != null && !sellerFilter.equals("Alla") && filteredItems.stream().anyMatch(item -> !item.isCollectedBySeller());
        view.enableButton("Betala ut", enablePayout);
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
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Öppna annan kassa-fil");
        // Optional: Filter for specific file types, e.g., CSV files
        FileNameExtensionFilter filter = new FileNameExtensionFilter("CSV Files", "csv");
        fileChooser.setFileFilter(filter);

        int result = fileChooser.showOpenDialog(null); // Pass your JFrame here if needed
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                List<SoldItem> importedItems = FormatHelper.toItems(FileHelper.readFromFile(Paths.get(file.getAbsolutePath())), true);
                int numberOfImportedItems = 0;
                for (SoldItem item : importedItems) {
                    if (!allHistoryItems.contains(item)) {
                        allHistoryItems.add(item);
                        numberOfImportedItems++;
                    }
                }
                FileHelper.createBackupFile();
                FileHelper.saveToFile(FormatHelper.toCVS(allHistoryItems));
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

    private void payout() {
        // Get filtered items
        List<SoldItem> filteredItems = applyFilters();
        // Iterate over filtered items and mark them as collected by seller (Local date now)
        LocalDateTime now = LocalDateTime.now();
        // Update allHistoryItems with changes made to filteredItems
        allHistoryItems.forEach(historyItem -> filteredItems.stream()
                .filter(filteredItem -> filteredItem.getItemId().equals(historyItem.getItemId())) // Assuming getItemId() is your unique identifier method
                .findFirst()
                .ifPresent(filteredItem -> historyItem.setCollectedBySellerTime(now)));
        String csv = FormatHelper.toCVS(allHistoryItems);
        // Write CSV string to file
        try {
            FileHelper.createBackupFile();
            FileHelper.saveToFile(csv);
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
        boolean hidePaidPosts = view.isPaidPostsHidden();
        String sellerFilter = view.getSellerFilter();
        String paymentMethodFilter = view.getPaymentMethodFilter();
        return allHistoryItems.stream()
                // If hidePaidPosts is true, filter out items that have been collected by the seller
                .filter(item -> !hidePaidPosts || !item.isCollectedBySeller())
                // Apply the seller filter if a specific seller is selected (not null and not "Alla")
                .filter(item -> sellerFilter == null || "Alla".equals(sellerFilter) || Filter.getFilterFunc(Filter.SELLER, sellerFilter).test(item))
                // Apply the payment method filter if a specific payment method is selected (not null and not "Alla")
                .filter(item -> paymentMethodFilter == null || "Alla".equals(paymentMethodFilter) || Filter.getFilterFunc(Filter.PAYMENT_METHOD, paymentMethodFilter).test(item))
                .collect(Collectors.toList());
    }
}
