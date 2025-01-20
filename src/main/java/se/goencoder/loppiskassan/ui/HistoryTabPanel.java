package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.controller.HistoryTabController;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Set;

import static se.goencoder.loppiskassan.ui.Constants.*;
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

    /**
     * Helper method to create a label + combobox in the filter panel
     * and place them in the given GridBagConstraints row.
     */
    private void addFilterRow(JPanel panel, GridBagConstraints gbc,
                              String labelText, JComboBox<String> comboBox, String[] comboItems) {
        // Label
        gbc.gridx = 0;
        panel.add(new JLabel(labelText), gbc);
        // Combobox
        gbc.gridx++;
        comboBox.setModel(new DefaultComboBoxModel<>(comboItems));
        panel.add(comboBox, gbc);
        // Move to the next row
        gbc.gridy++;
        gbc.gridx = 0;
    }

    private JPanel initializeFilterPanel() {
        JPanel filterPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(2, 2, 2, 2);
        gbc.weightx = 0;
        gbc.gridy = 0;
        gbc.gridx = 0;

        // Paid filter
        paidFilterDropdown = new JComboBox<>();
        addFilterRow(filterPanel, gbc, "Utbetalt", paidFilterDropdown,
                new String[]{"Alla", "Ja", "Nej"});

        // Seller filter
        sellerFilterDropdown = new JComboBox<>();
        addFilterRow(filterPanel, gbc, "Säljnummer", sellerFilterDropdown,
                new String[]{"Alla"});

        // Payment method filter
        paymentTypeFilterDropdown = new JComboBox<>();
        addFilterRow(filterPanel, gbc, "Betalningsmedel", paymentTypeFilterDropdown,
                new String[]{"Alla", "Swish", "Kontant"});

        // Add horizontal "glue"
        gbc.weightx = 1;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        filterPanel.add(Box.createHorizontalGlue(), gbc);
        gbc.gridy++;
        gbc.gridx = 0;
        gbc.weighty = 1;
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
        JPanel managementButtonsPanel = new JPanel();
        managementButtonsPanel.setLayout(new BoxLayout(managementButtonsPanel, BoxLayout.PAGE_AXIS));

        Dimension buttonSize = new Dimension(150, 50);

        // Create buttons
        eraseAllDataButton = createButton(BUTTON_ERASE, buttonSize.width, buttonSize.height);
        archiveFilteredButton = createButton("Arkivera filtrerat", buttonSize.width, buttonSize.height);
        importDataButton = createButton(BUTTON_IMPORT, buttonSize.width, buttonSize.height);

        // Wrap each button in its own FlowLayout panel
        managementButtonsPanel.add(createFlowRightPanel(eraseAllDataButton));
        managementButtonsPanel.add(createFlowRightPanel(archiveFilteredButton));
        managementButtonsPanel.add(createFlowRightPanel(importDataButton));

        // Add action listeners
        eraseAllDataButton.addActionListener(e -> controller.buttonAction(BUTTON_ERASE));
        archiveFilteredButton.addActionListener(e -> controller.buttonAction(BUTTON_ARCHIVE));
        importDataButton.addActionListener(e -> controller.buttonAction(BUTTON_IMPORT));

        // Wrap the vertical panel with a border layout panel
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(managementButtonsPanel, BorderLayout.NORTH);

        return wrapperPanel;
    }

    /**
     * Small helper to create a FlowLayout RIGHT-aligned panel containing one button.
     */
    private JPanel createFlowRightPanel(JButton button) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.add(button);
        return panel;
    }

    private JPanel initializeActionButtons() {
        JPanel actionButtonsPanel = new JPanel(new BorderLayout());
        JPanel innerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        payoutButton = createButton(BUTTON_PAY_OUT, 150, 50);
        toClipboardButton = createButton(BUTTON_COPY_TO_CLIPBOARD, 150, 50);

        innerPanel.add(payoutButton);
        innerPanel.add(toClipboardButton);

        payoutButton.addActionListener(e -> controller.buttonAction(BUTTON_PAY_OUT));
        toClipboardButton.addActionListener(e -> controller.buttonAction(BUTTON_COPY_TO_CLIPBOARD));

        actionButtonsPanel.add(innerPanel, BorderLayout.CENTER);
        actionButtonsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        return actionButtonsPanel;
    }

    @Override
    public void updateHistoryTable(List<SoldItem> items) {
        DefaultTableModel model = (DefaultTableModel) historyTable.getModel();
        model.setRowCount(0);
        // Iterate backwards to add the latest items first
        for (int i = items.size() - 1; i >= 0; i--) {
            SoldItem item = items.get(i);
            model.addRow(new Object[]{
                    item.getSeller(),
                    item.getPrice() + " SEK",
                    item.getSoldTime().toString(),
                    item.isCollectedBySeller() ? "Ja" : "Nej",
                    item.getPaymentMethod().toString()
            });
        }

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
        return sellerFilterDropdown.getSelectedIndex() == 0
                ? null
                : (String) sellerFilterDropdown.getSelectedItem();
    }

    @Override
    public String getPaymentMethodFilter() {
        return paymentTypeFilterDropdown.getSelectedIndex() == 0
                ? null
                : (String) paymentTypeFilterDropdown.getSelectedItem();
    }

    @Override
    public void clearView() {
        DefaultTableModel model = (DefaultTableModel) historyTable.getModel();
        model.setRowCount(0);
        itemsCountLabel.setText("Antal varor: 0");
        totalSumLabel.setText("Summa: 0 SEK");
        sellerFilterDropdown.setSelectedIndex(0);
        paymentTypeFilterDropdown.setSelectedIndex(0);
        paidFilterDropdown.setSelectedIndex(0);
    }

    @Override
    public void updateSellerDropdown(Set<String> sellers) {
        sellerFilterDropdown.removeAllItems();
        sellerFilterDropdown.addItem("Alla");
        // iterate sellers, sort them numerical, and add them
        // Sort the sellers numerically and add them to the dropdown
        sellers.stream()
                .map(Integer::parseInt)
                .sorted()
                .map(String::valueOf)
                .forEach(sellerFilterDropdown::addItem);
    }

    @Override
    public void enableButton(String buttonName, boolean enable) {
        switch (buttonName) {
            case BUTTON_ERASE:
                eraseAllDataButton.setEnabled(enable);
                break;
            case BUTTON_IMPORT:
                importDataButton.setEnabled(enable);
                break;
            case BUTTON_PAY_OUT:
                payoutButton.setEnabled(enable);
                break;
            case BUTTON_COPY_TO_CLIPBOARD:
                toClipboardButton.setEnabled(enable);
                break;
            case BUTTON_ARCHIVE:
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
