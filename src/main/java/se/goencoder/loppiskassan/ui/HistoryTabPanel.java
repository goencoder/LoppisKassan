package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.controller.HistoryControllerInterface;
import se.goencoder.loppiskassan.controller.HistoryTabController;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Set;

import static se.goencoder.loppiskassan.ui.Constants.*;
import static se.goencoder.loppiskassan.ui.UserInterface.createButton;

/**
 * Represents the "History" tab in the application, allowing users to view and manage sold items.
 */
public class HistoryTabPanel extends JPanel implements HistoryPanelInterface {

    // UI components
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

    // Controller to manage business logic for this panel
    private final HistoryControllerInterface controller = HistoryTabController.getInstance();

    /**
     * Initializes the History tab panel, including its layout and components.
     */
    public HistoryTabPanel() {
        // Use BorderLayout for organizing the main sections
        setLayout(new BorderLayout());

        // Create and add the top panel with filters and management buttons
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.add(initializeFilterPanel(), BorderLayout.CENTER); // Filters
        topPanel.add(initializeManagementButtons(), BorderLayout.EAST); // Management buttons
        add(topPanel, BorderLayout.NORTH);

        // Create and add the center panel with the history table and summary
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.add(initializeSummaryPanel(), BorderLayout.NORTH); // Summary above table
        tablePanel.add(new JScrollPane(initializeTable()), BorderLayout.CENTER); // Table
        add(tablePanel, BorderLayout.CENTER);

        // Create and add the bottom panel with action buttons
        add(initializeActionButtons(), BorderLayout.SOUTH);

        // Register this panel with the controller
        controller.registerView(this);
    }

    /**
     * Creates and initializes the history table for displaying sold items.
     *
     * @return The initialized JTable.
     */
    private JTable initializeTable() {
        // Define column names for the table
        String[] columnNames = {"Säljare", "Pris", "Sålt", "Utbetalt", "Betalningsmedel"};
        DefaultTableModel model = new DefaultTableModel(null, columnNames);

        // Create the table with a non-editable model
        historyTable = new JTable(model);
        return historyTable;
    }

    /**
     * Creates the filter panel with dropdowns for filtering the history table.
     *
     * @return The filter panel.
     */
    private JPanel initializeFilterPanel() {
        JPanel filterPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.NORTHWEST; // Align components to the top-left
        gbc.insets = new Insets(2, 2, 2, 2); // Add padding between components
        gbc.weightx = 0; // Do not stretch horizontally
        gbc.gridy = 0;
        gbc.gridx = 0;

        // Paid filter dropdown
        paidFilterDropdown = new JComboBox<>();
        addFilterRow(filterPanel, gbc, "Utbetalt", paidFilterDropdown, new String[]{"Alla", "Ja", "Nej"});

        // Seller filter dropdown
        sellerFilterDropdown = new JComboBox<>();
        addFilterRow(filterPanel, gbc, "Säljnummer", sellerFilterDropdown, new String[]{"Alla"});

        // Payment method filter dropdown
        paymentTypeFilterDropdown = new JComboBox<>();
        addFilterRow(filterPanel, gbc, "Betalningsmedel", paymentTypeFilterDropdown, new String[]{"Alla", "Swish", "Kontant"});

        // Add flexible space to push components to the top
        gbc.weightx = 1;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        filterPanel.add(Box.createHorizontalGlue(), gbc);
        gbc.gridy++;
        gbc.weighty = 1;
        filterPanel.add(Box.createVerticalGlue(), gbc);

        // Add listeners to update the table when filters change
        paidFilterDropdown.addActionListener(e -> controller.filterUpdated());
        sellerFilterDropdown.addActionListener(e -> controller.filterUpdated());
        paymentTypeFilterDropdown.addActionListener(e -> controller.filterUpdated());
        return filterPanel;
    }

    /**
     * Adds a row with a label and dropdown to the filter panel.
     *
     * @param panel     The filter panel.
     * @param gbc       The GridBagConstraints for layout.
     * @param labelText The label text.
     * @param comboBox  The dropdown component.
     * @param comboItems The items for the dropdown.
     */
    private void addFilterRow(JPanel panel, GridBagConstraints gbc, String labelText, JComboBox<String> comboBox, String[] comboItems) {
        // Add the label
        gbc.gridx = 0;
        panel.add(new JLabel(labelText), gbc);

        // Add the dropdown
        gbc.gridx++;
        comboBox.setModel(new DefaultComboBoxModel<>(comboItems));
        panel.add(comboBox, gbc);

        // Move to the next row
        gbc.gridy++;
        gbc.gridx = 0;
    }

    /**
     * Creates the summary panel to display total items and total price.
     *
     * @return The summary panel.
     */
    private JPanel initializeSummaryPanel() {
        JPanel summaryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        itemsCountLabel = new JLabel("Antal varor: 0");
        totalSumLabel = new JLabel("Summa: 0 SEK");
        summaryPanel.add(itemsCountLabel);
        summaryPanel.add(totalSumLabel);
        return summaryPanel;
    }

    /**
     * Creates the panel with management buttons like "Erase All" and "Archive Filtered."
     *
     * @return The management buttons panel.
     */
    private JPanel initializeManagementButtons() {
        JPanel managementButtonsPanel = new JPanel();
        managementButtonsPanel.setLayout(new BoxLayout(managementButtonsPanel, BoxLayout.PAGE_AXIS));

        Dimension buttonSize = new Dimension(150, 50);

        // Initialize buttons
        eraseAllDataButton = createButton(BUTTON_ERASE, buttonSize.width, buttonSize.height);
        archiveFilteredButton = createButton("Arkivera filtrerat", buttonSize.width, buttonSize.height);
        importDataButton = createButton(BUTTON_IMPORT, buttonSize.width, buttonSize.height);

        // Add buttons to the panel
        managementButtonsPanel.add(createFlowRightPanel(eraseAllDataButton));
        managementButtonsPanel.add(createFlowRightPanel(archiveFilteredButton));
        managementButtonsPanel.add(createFlowRightPanel(importDataButton));

        // Add action listeners for buttons
        eraseAllDataButton.addActionListener(e -> controller.buttonAction(BUTTON_ERASE));
        archiveFilteredButton.addActionListener(e -> controller.buttonAction(BUTTON_ARCHIVE));
        importDataButton.addActionListener(e -> controller.buttonAction(BUTTON_IMPORT));

        // Wrap the panel for better layout
        JPanel wrapperPanel = new JPanel(new BorderLayout());
        wrapperPanel.add(managementButtonsPanel, BorderLayout.NORTH);

        return wrapperPanel;
    }

    /**
     * Creates a panel for the action buttons at the bottom of the tab.
     *
     * @return The action buttons panel.
     */
    private JPanel initializeActionButtons() {
        JPanel actionButtonsPanel = new JPanel(new BorderLayout());
        JPanel innerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        // Initialize buttons
        payoutButton = createButton(BUTTON_PAY_OUT, 150, 50);
        toClipboardButton = createButton(BUTTON_COPY_TO_CLIPBOARD, 150, 50);

        // Add buttons to the inner panel
        innerPanel.add(payoutButton);
        innerPanel.add(toClipboardButton);

        // Add action listeners
        payoutButton.addActionListener(e -> controller.buttonAction(BUTTON_PAY_OUT));
        toClipboardButton.addActionListener(e -> controller.buttonAction(BUTTON_COPY_TO_CLIPBOARD));

        actionButtonsPanel.add(innerPanel, BorderLayout.CENTER);
        actionButtonsPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        return actionButtonsPanel;
    }

    /**
     * Helper to create a right-aligned panel for a single button.
     *
     * @param button The button to add.
     * @return The right-aligned panel.
     */
    private JPanel createFlowRightPanel(JButton button) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.add(button);
        return panel;
    }

    // Implementations of HistoryPanelInterface methods
    @Override
    public void updateHistoryTable(List<SoldItem> items) {
        DefaultTableModel model = (DefaultTableModel) historyTable.getModel();
        model.setRowCount(0);
        for (SoldItem item : items) {
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
        return sellerFilterDropdown.getSelectedIndex() == 0 ? null : (String) sellerFilterDropdown.getSelectedItem();
    }

    @Override
    public String getPaymentMethodFilter() {
        return paymentTypeFilterDropdown.getSelectedIndex() == 0 ? null : (String) paymentTypeFilterDropdown.getSelectedItem();
    }


    @Override
    public void updateSellerDropdown(Set<String> sellers) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement("Alla");
        sellers.stream()
                .map(Integer::parseInt)
                .sorted()
                .map(String::valueOf)
                .forEach(model::addElement);

        sellerFilterDropdown.setModel(model);
    }

    @Override
    public void enableButton(String buttonName, boolean enable) {
        switch (buttonName) {
            case BUTTON_ERASE -> eraseAllDataButton.setEnabled(enable);
            case BUTTON_IMPORT -> importDataButton.setEnabled(enable);
            case BUTTON_PAY_OUT -> payoutButton.setEnabled(enable);
            case BUTTON_COPY_TO_CLIPBOARD -> toClipboardButton.setEnabled(enable);
            case BUTTON_ARCHIVE -> archiveFilteredButton.setEnabled(enable);
            default -> System.err.println("Unknown button name: " + buttonName);
        }
    }

    @Override
    public void setImportButtonText(String text) {
        importDataButton.setText(text);
    }

    @Override
    public void selected() {
        controller.loadHistory();
        controller.filterUpdated();
    }

    @Override
    public Component getComponent() {
        return this;
    }
}
