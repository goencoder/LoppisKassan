package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.controller.HistoryTabController;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Set;

import static se.goencoder.loppiskassan.ui.UserInterface.createButton;

public class HistoryTabPanel extends JPanel implements HistoryPanelInterface {
    private JTable historyTable;
    private JButton eraseAllDataButton;
    private JButton archiveFilteredButton;
    private JButton importDataButton;
    private JButton payoutButton;
    private JButton toClipboardButton;

    private JComboBox<String> paidFilterDropdown;
    private JComboBox<String> sellerFilterDropdown;
    private JComboBox<String> paymentTypeFilterDropdown;
    private JLabel itemsCountLabel, totalSumLabel;

    private final HistoryTabController controller = HistoryTabController.getInstance();

    public HistoryTabPanel() {
        setLayout(new BorderLayout());

        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(initializeFilterPanel(), BorderLayout.CENTER);
        topPanel.add(initializeManagementButtons(), BorderLayout.EAST); // Management buttons to the right

        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.add(initializeSummaryPanel(), BorderLayout.NORTH); // Summary above table
        tablePanel.add(new JScrollPane(initializeTable()), BorderLayout.CENTER);

        add(topPanel, BorderLayout.NORTH);
        add(tablePanel, BorderLayout.CENTER);
        add(initializeActionButtons(), BorderLayout.SOUTH); // Action buttons at the bottom

        controller.registerView(this);
    }
    private JTable initializeTable() {
        String[] columnNames = {"Säljare", "Pris", "Sålt", "Utbetalt", "Betalningsmetod"};
        DefaultTableModel model = new DefaultTableModel(null, columnNames);
        historyTable = new JTable(model);
        return historyTable;
    }

    private JPanel initializeFilterPanel() {
        JPanel filterPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();

        gbc.anchor = GridBagConstraints.NORTHWEST; // Anchor components to the top-left corner
        gbc.insets = new Insets(2, 2, 2, 2); // Standard padding
        gbc.weightx = 0;  // Do not stretch components horizontally


        gbc.gridy = 0; // Start at the first row
        gbc.gridx = 0; // Column 0 for the label
        JLabel paidLabel = new JLabel("Utbetalt");
        filterPanel.add(paidLabel, gbc);

        gbc.gridx = 1; // Column 1 for the dropdown
        paidFilterDropdown = new JComboBox<>(new String[]{ "Alla", "Ja", "Nej"});
        filterPanel.add(paidFilterDropdown, gbc);

        // Seller Filter Dropdown
        gbc.gridx = 0; // Reset to first column for the label
        gbc.gridy++; // Next row
        gbc.gridwidth = 1; // Reset to one column span for the label
        JLabel sellerLabel = new JLabel("Säljnummer");
        filterPanel.add(sellerLabel, gbc);

        gbc.gridx++; // Move to next column for the dropdown
        sellerFilterDropdown = new JComboBox<>(new String[]{"Alla"});
        filterPanel.add(sellerFilterDropdown, gbc);

        // Payment Type Filter Dropdown
        gbc.gridx = 0; // Reset to first column for the label
        gbc.gridy++; // Next row
        JLabel paymentLabel = new JLabel("Betalningsmedel");
        filterPanel.add(paymentLabel, gbc);

        gbc.gridx++; // Move to next column for the dropdown
        paymentTypeFilterDropdown = new JComboBox<>(new String[]{"Alla", "Swish", "Kontant"});
        filterPanel.add(paymentTypeFilterDropdown, gbc);

        // Add "glue" to absorb all remaining horizontal space
        gbc.gridx++; // Next column after the dropdowns
        gbc.weightx = 1;  // This component will absorb all extra horizontal space
        gbc.gridwidth = GridBagConstraints.REMAINDER; // This component fills the rest of the row
        filterPanel.add(Box.createHorizontalGlue(), gbc);

        // Add "glue" component to push everything to the top
        gbc.gridy++; // Row after the last component
        gbc.gridx = 0; // Start from the first column
        gbc.weighty = 1;  // Occupy all vertical space at the end
        gbc.gridwidth = GridBagConstraints.REMAINDER; // Span all columns
        filterPanel.add(Box.createVerticalGlue(), gbc);

        // Register filter update handlers
        paidFilterDropdown.addActionListener(e -> controller.filterUpdated());
        sellerFilterDropdown.addActionListener(e -> controller.filterUpdated());
        paymentTypeFilterDropdown.addActionListener(e -> controller.filterUpdated());

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


    private JPanel initializeManagementButtons() {
        // Same vertical BoxLayout as before
        JPanel managementButtonsPanel = new JPanel();
        managementButtonsPanel.setLayout(new BoxLayout(managementButtonsPanel, BoxLayout.PAGE_AXIS));

        // Set a uniform size for all buttons
        Dimension buttonSize = new Dimension(150, 50); // You can adjust width and height as needed

        eraseAllDataButton = createButton("Rensa kassan", buttonSize.width, buttonSize.height);
        archiveFilteredButton = createButton("Arkivera filtrerat", buttonSize.width, buttonSize.height);
        importDataButton = createButton("Importera kassa", buttonSize.width, buttonSize.height);

        // Wrap buttons individually in panels with FlowLayout to align them to the right
        JPanel erasePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        erasePanel.add(eraseAllDataButton);
        JPanel archivePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        archivePanel.add(archiveFilteredButton);
        JPanel importPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        importPanel.add(importDataButton);

        // Add action listeners
        eraseAllDataButton.addActionListener(e -> controller.buttonAction("Rensa kassan"));
        archiveFilteredButton.addActionListener(e -> controller.buttonAction("Arkivera visade poster"));
        importDataButton.addActionListener(e -> controller.buttonAction("Importera kassa"));

        // Add the individual panels to the main management panel
        managementButtonsPanel.add(erasePanel);
        managementButtonsPanel.add(archivePanel);
        managementButtonsPanel.add(importPanel);

        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(managementButtonsPanel, BorderLayout.NORTH);

        return wrapperPanel;
    }

    private JPanel initializeActionButtons() {
        // Action buttons panel that stretches across the window
        JPanel actionButtonsPanel = new JPanel(new BorderLayout());

        // Inner panel with flow layout centers the buttons
        JPanel innerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10)); // added horizontal and vertical gaps

        // Assuming button size has already been set with setPreferredSize in createButton method
        payoutButton = createButton("Betala ut", 150, 50); // width and height adjusted as needed
        toClipboardButton = createButton("Kopiera till urklipp", 150, 50);

        innerPanel.add(payoutButton);
        innerPanel.add(toClipboardButton);

        // Add action listeners to buttons
        payoutButton.addActionListener(e -> controller.buttonAction("Betala ut"));
        toClipboardButton.addActionListener(e -> controller.buttonAction("Kopiera till urklipp"));

        // Add innerPanel to the center of actionButtonsPanel to make it stretch across the window
        actionButtonsPanel.add(innerPanel, BorderLayout.CENTER);

        // Add some padding if needed
        actionButtonsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // top, left, bottom, right padding

        return actionButtonsPanel;
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
    public String getPaidFilter() {
        return (String) paidFilterDropdown.getSelectedItem();
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
        paidFilterDropdown.setSelectedIndex(0);
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
            case "Arkivera visade poster":
                archiveFilteredButton.setEnabled(enable);
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
