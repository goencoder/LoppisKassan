package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.controller.HistoryTabController;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Set;

public class HistoryTabPanel extends JPanel implements HistoryPanelInterface {
    private JTable historyTable;
    private JButton eraseAllDataButton, importDataButton, payoutButton, toClipboardButton;

    private JCheckBox paidPostsCheckBox;
    private JComboBox<String> sellerFilterDropdown;
    private JComboBox<String> paymentTypeFilterDropdown;
    private JLabel itemsCountLabel, totalSumLabel;

    private final HistoryTabController controller = HistoryTabController.getInstance();

    public HistoryTabPanel() {
        setLayout(new BorderLayout());
        add(initializeTopPanel(), BorderLayout.NORTH);
        add(new JScrollPane(initializeTable()), BorderLayout.CENTER);
        add(initializeButtonPanel(), BorderLayout.SOUTH);
        controller.registerView(this);
    }

    private JTable initializeTable() {
        String[] columnNames = {"Säljare", "Pris", "Sålt", "Utbetalt", "Betalningsmetod"};
        DefaultTableModel model = new DefaultTableModel(null, columnNames);
        historyTable = new JTable(model);
        return historyTable;
    }

    private JPanel initializeTopPanel() {
        JPanel topPanel = new JPanel(new GridLayout(2, 1));
        topPanel.add(initializeFilterPanel());
        topPanel.add(initializeSummaryPanel());
        return topPanel;
    }

    private JPanel initializeFilterPanel() {
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        paidPostsCheckBox = new JCheckBox("Dölj utbetalda poster");
        paidPostsCheckBox.addActionListener(e -> controller.filterUpdated());
        sellerFilterDropdown = new JComboBox<>(new String[]{"Alla"});
        sellerFilterDropdown.addActionListener(e -> controller.filterUpdated());
        paymentTypeFilterDropdown = new JComboBox<>(new String[]{"Alla", "Swish", "Kontant"});
        paymentTypeFilterDropdown.addActionListener(e -> controller.filterUpdated());
        filterPanel.add(paidPostsCheckBox);
        filterPanel.add(new JLabel("Filtrera på säljare"));
        filterPanel.add(sellerFilterDropdown);
        filterPanel.add(new JLabel("Typ av betalning"));
        filterPanel.add(paymentTypeFilterDropdown);
        return filterPanel;
    }

    private JPanel initializeSummaryPanel() {
        JPanel summaryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        itemsCountLabel = new JLabel("Antal varor: 0");
        totalSumLabel = new JLabel("Summa: 0 SEK");
        summaryPanel.add(itemsCountLabel);
        summaryPanel.add(totalSumLabel);
        return summaryPanel;
    }

    private JPanel initializeButtonPanel() {
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        // Initialize buttons with direct references
        eraseAllDataButton = new JButton("Rensa kassan");
        importDataButton = new JButton("Importera kassa");
        payoutButton = new JButton("Betala ut");
        toClipboardButton = new JButton("Kopiera till urklipp");

        payoutButton.addActionListener(e -> controller.buttonAction("Betala ut"));
        eraseAllDataButton.addActionListener(e -> controller.buttonAction("Rensa kassan"));
        importDataButton.addActionListener(e -> controller.buttonAction("Importera kassa"));
        toClipboardButton.addActionListener(e -> controller.buttonAction("Kopiera till urklipp"));

        // Add action listeners as before
        // Add buttons to panel
        buttonPanel.add(eraseAllDataButton);
        buttonPanel.add(importDataButton);
        buttonPanel.add(payoutButton);
        buttonPanel.add(toClipboardButton);

        return buttonPanel;
    }

    @Override
    public void updateHistoryTable(List<SoldItem> items) {
        DefaultTableModel model = (DefaultTableModel) historyTable.getModel();
        model.setRowCount(0);
        items.forEach(item -> model.addRow(new Object[]{
                item.getSeller(), item.getPrice() + " SEK", item.getSoldTime().toString(),
                item.isCollectedBySeller() ? "Ja" : "Nej", item.getPaymentMethod().toString()
        }));
    }

    @Override
    public void updateSumLabel(String sum) {
        totalSumLabel.setText("Summa: " + sum + " SEK");
    }

    @Override
    public void updateNoItemsLabel(String noItems) {
        itemsCountLabel.setText("Antal varor: " + noItems);
    }

    @Override
    public String getSellerFilter() {
        return sellerFilterDropdown.getSelectedIndex() == 0 ? null : (String) sellerFilterDropdown.getSelectedItem();
    }

    @Override
    public String getPaymentMethodFilter() {
        return paymentTypeFilterDropdown.getSelectedIndex() == 0 ? null : (String) paymentTypeFilterDropdown.getSelectedItem();
    }

    @Override
    public void clearView() {
        DefaultTableModel model = (DefaultTableModel) historyTable.getModel();
        model.setRowCount(0);
        itemsCountLabel.setText("Antal varor: 0");
        totalSumLabel.setText("Summa: 0 SEK");
        // Reset filter dropdowns
        sellerFilterDropdown.setSelectedIndex(0);
        paymentTypeFilterDropdown.setSelectedIndex(0);
        paidPostsCheckBox.setSelected(false);
    }

    @Override
    public boolean isPaidPostsHidden() {
        return paidPostsCheckBox.isSelected();
    }

    @Override
    public void updateSellerDropdown(Set<String> sellers) {
        sellerFilterDropdown.removeAllItems();
        sellerFilterDropdown.addItem("Alla");
        sellers.forEach(sellerFilterDropdown::addItem);
    }

    @Override
    public void enableButton(String buttonName, boolean enable) {
        // Directly manipulate button state based on the buttonName argument
        switch (buttonName) {
            case "Rensa kassan":
                eraseAllDataButton.setEnabled(enable);
                break;
            case "Importera kassa":
                importDataButton.setEnabled(enable);
                break;
            case "Betala ut":
                payoutButton.setEnabled(enable);
                break;
            case "Kopiera till urklipp":
                toClipboardButton.setEnabled(enable);
                break;
            default:
                System.err.println("Unknown button name: " + buttonName);
        }
    }

    @Override
    public void selected() {
        controller.loadHistory();
    }
}
