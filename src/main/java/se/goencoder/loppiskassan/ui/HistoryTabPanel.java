package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.PaymentMethod;
import se.goencoder.loppiskassan.controller.HistoryControllerInterface;
import se.goencoder.loppiskassan.controller.HistoryTabController;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.localization.LocalizationAware;

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
public class HistoryTabPanel extends JPanel implements HistoryPanelInterface, LocalizationAware {

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
    private JLabel paidFilterLabel, sellerFilterLabel, paymentFilterLabel;

    private int itemsCount = 0;
    private int totalSum = 0;

    // Controller to manage business logic for this panel
    private final HistoryControllerInterface controller = HistoryTabController.getInstance();

    /**
     * Initializes the History tab panel, including its layout and components.
     */
    public HistoryTabPanel() {
        // Use BorderLayout for organizing the main sections
        setLayout(new BorderLayout());
        LocalizationManager.addListener(this::reloadTexts);

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
        reloadTexts();
    }

    /**
     * Creates and initializes the history table for displaying sold items.
     *
     * @return The initialized JTable.
     */
    private JTable initializeTable() {
        // Define column names for the table
        String[] columnNames = {
                LocalizationManager.tr("history.table.seller"),
                LocalizationManager.tr("history.table.price"),
                LocalizationManager.tr("history.table.sold"),
                LocalizationManager.tr("history.table.paid_out"),
                LocalizationManager.tr("history.table.payment_method")
        };
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
        paidFilterLabel = addFilterRow(
                filterPanel,
                gbc,
                LocalizationManager.tr("filter.paid"),
                paidFilterDropdown,
                new String[]{
                        LocalizationManager.tr("filter.all"),
                        LocalizationManager.tr("filter.yes"),
                        LocalizationManager.tr("filter.no")
                }
        );

        // Seller filter dropdown
        sellerFilterDropdown = new JComboBox<>();
        sellerFilterLabel = addFilterRow(
                filterPanel,
                gbc,
                LocalizationManager.tr("filter.seller"),
                sellerFilterDropdown,
                new String[]{LocalizationManager.tr("filter.all")}
        );

        // Payment method filter dropdown
        paymentTypeFilterDropdown = new JComboBox<>();
        paymentFilterLabel = addFilterRow(
                filterPanel,
                gbc,
                LocalizationManager.tr("filter.payment_method"),
                paymentTypeFilterDropdown,
                new String[]{
                        LocalizationManager.tr("filter.all"),
                        LocalizationManager.tr("payment.swish"),
                        LocalizationManager.tr("payment.cash")
                }
        );

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
    private JLabel addFilterRow(JPanel panel, GridBagConstraints gbc, String labelText, JComboBox<String> comboBox, String[] comboItems) {
        // Add the label
        gbc.gridx = 0;
        JLabel label = new JLabel(labelText);
        panel.add(label, gbc);

        // Add the dropdown
        gbc.gridx++;
        comboBox.setModel(new DefaultComboBoxModel<>(comboItems));
        panel.add(comboBox, gbc);

        // Move to the next row
        gbc.gridy++;
        gbc.gridx = 0;
        return label;
    }

    /**
     * Creates the summary panel to display total items and total price.
     *
     * @return The summary panel.
     */
    private JPanel initializeSummaryPanel() {
        JPanel summaryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        itemsCountLabel = new JLabel();
        totalSumLabel = new JLabel();
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
        eraseAllDataButton = createButton("", buttonSize.width, buttonSize.height);
        archiveFilteredButton = createButton("", buttonSize.width, buttonSize.height);
        importDataButton = createButton("", buttonSize.width, buttonSize.height);

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
        payoutButton = createButton("", 150, 50);
        toClipboardButton = createButton("", 150, 50);

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

    @Override
    public void addNotify() {
        super.addNotify();
        LocalizationManager.addListener(this::reloadTexts);
    }

    @Override
    public void removeNotify() {
        LocalizationManager.removeListener(this::reloadTexts);
        super.removeNotify();
    }

    @Override
    public void reloadTexts() {
        // Table headers
        DefaultTableModel model = (DefaultTableModel) historyTable.getModel();
        model.setColumnIdentifiers(new String[]{
                LocalizationManager.tr("history.table.seller"),
                LocalizationManager.tr("history.table.price"),
                LocalizationManager.tr("history.table.sold"),
                LocalizationManager.tr("history.table.paid_out"),
                LocalizationManager.tr("history.table.payment_method")
        });

        // Filters
        int paidIndex = paidFilterDropdown.getSelectedIndex();
        paidFilterDropdown.setModel(new DefaultComboBoxModel<>(new String[]{
                LocalizationManager.tr("filter.all"),
                LocalizationManager.tr("filter.yes"),
                LocalizationManager.tr("filter.no")
        }));
        paidFilterDropdown.setSelectedIndex(paidIndex);
        paidFilterLabel.setText(LocalizationManager.tr("filter.paid"));

        int sellerIndex = sellerFilterDropdown.getSelectedIndex();
        DefaultComboBoxModel<String> sellerModel = new DefaultComboBoxModel<>();
        sellerModel.addElement(LocalizationManager.tr("filter.all"));
        ComboBoxModel<String> currentSellerModel = sellerFilterDropdown.getModel();
        for (int i = 1; i < currentSellerModel.getSize(); i++) {
            sellerModel.addElement(currentSellerModel.getElementAt(i));
        }
        sellerFilterDropdown.setModel(sellerModel);
        sellerFilterDropdown.setSelectedIndex(sellerIndex);
        sellerFilterLabel.setText(LocalizationManager.tr("filter.seller"));

        int paymentIndex = paymentTypeFilterDropdown.getSelectedIndex();
        paymentTypeFilterDropdown.setModel(new DefaultComboBoxModel<>(new String[]{
                LocalizationManager.tr("filter.all"),
                LocalizationManager.tr("payment.swish"),
                LocalizationManager.tr("payment.cash")
        }));
        paymentTypeFilterDropdown.setSelectedIndex(paymentIndex);
        paymentFilterLabel.setText(LocalizationManager.tr("filter.payment_method"));

        // Buttons
        eraseAllDataButton.setText(LocalizationManager.tr(BUTTON_ERASE));
        archiveFilteredButton.setText(LocalizationManager.tr("button.archive_filtered"));
        importDataButton.setText(LocalizationManager.tr(BUTTON_IMPORT));
        payoutButton.setText(LocalizationManager.tr(BUTTON_PAY_OUT));
        toClipboardButton.setText(LocalizationManager.tr(BUTTON_COPY_TO_CLIPBOARD));

        // Summary labels
        itemsCountLabel.setText(LocalizationManager.tr("history.items_count", itemsCount));
        totalSumLabel.setText(LocalizationManager.tr("history.total_sum", totalSum));
    }

    // Implementations of HistoryPanelInterface methods
    @Override
    public void updateHistoryTable(List<SoldItem> items) {
        // Ensure table updates happen on the EDT to avoid threading issues
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> updateHistoryTable(items));
            return;
        }

        // Get current table model
        DefaultTableModel model = (DefaultTableModel) historyTable.getModel();

        // Clear the model safely
        model.setRowCount(0);

        // Only add rows if we have items
        if (items != null && !items.isEmpty()) {
            // Add data in batches to improve performance
            for (SoldItem item : items) {
                model.addRow(new Object[]{
                        item.getSeller(),
                        item.getPrice() + " " + LocalizationManager.tr("currency.sek"),
                        item.getSoldTime().toString(),
                        item.isCollectedBySeller() ? LocalizationManager.tr("common.yes") : LocalizationManager.tr("common.no"),
                        LocalizationManager.tr(item.getPaymentMethod() == PaymentMethod.Kontant ?
                                "payment.cash" : "payment.swish")
                });
            }
        }

        // Force UI update
        historyTable.revalidate();
        historyTable.repaint();
    }

    @Override
    public void updateSumLabel(String sum) {
        // Parse as double first to handle decimal numbers, then convert to int
        totalSum = (int) Double.parseDouble(sum);
        totalSumLabel.setText(LocalizationManager.tr("history.total_sum", totalSum));
    }

    @Override
    public void updateNoItemsLabel(String noItems) {
        // Parse as double first to handle decimal numbers, then convert to int
        itemsCount = (int) Double.parseDouble(noItems);
        itemsCountLabel.setText(LocalizationManager.tr("history.items_count", itemsCount));
    }

    @Override
    public String getPaidFilter() {
        return switch (paidFilterDropdown.getSelectedIndex()) {
            case 1 -> "true";
            case 2 -> "false";
            default -> null;
        };
    }

    @Override
    public String getSellerFilter() {
        return sellerFilterDropdown.getSelectedIndex() == 0 ? null : (String) sellerFilterDropdown.getSelectedItem();
    }

    @Override
    public String getPaymentMethodFilter() {
        return switch (paymentTypeFilterDropdown.getSelectedIndex()) {
            case 1 -> PaymentMethod.Swish.name();
            case 2 -> PaymentMethod.Kontant.name();
            default -> null;
        };
    }


    @Override
    public void updateSellerDropdown(Set<String> sellers) {
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        model.addElement(LocalizationManager.tr("filter.all"));
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
