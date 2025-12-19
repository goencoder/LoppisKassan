package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.PaymentMethod;
import se.goencoder.loppiskassan.controller.HistoryControllerInterface;
import se.goencoder.loppiskassan.controller.HistoryTabController;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.config.ConfigurationStore;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.Dimension;
import java.awt.Insets;
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
    private JTextField searchField;
    private JLabel searchLabel;
    private Dimension filterFieldSize; // shared width/height for dropdowns + search
    private JLabel itemsCountLabel, totalSumLabel;
    private JLabel paidFilterLabel, sellerFilterLabel, paymentFilterLabel;

    private int itemsCount = 0;
    private int totalSum = 0;

    // Table filtering/sorting
    private TableRowSorter<DefaultTableModel> sorter;

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
        historyTable = new JTable(model) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        historyTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        historyTable.setRowHeight(28);
        historyTable.putClientProperty("Table.alternateRowColor", Boolean.TRUE);

        // Right-align price column; center the date-time column for readability
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        historyTable.getColumnModel().getColumn(1).setCellRenderer(right);
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        historyTable.getColumnModel().getColumn(2).setCellRenderer(center);

        // Sorter (also used for the Search filter)
        sorter = new TableRowSorter<>(model);
        historyTable.setRowSorter(sorter);

        return historyTable;
    }

    /**
     * Creates the filter panel with dropdowns for filtering the history table.
     *
     * @return The filter panel.
     */
    private JPanel initializeFilterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        Insets insets = new Insets(6, 16, 6, 16);

        GridBagConstraints lbl = new GridBagConstraints();
        lbl.anchor = GridBagConstraints.LINE_END;
        lbl.insets = insets;
        lbl.gridx = 0;

        GridBagConstraints fld = new GridBagConstraints();
        fld.gridx = 1;
        fld.fill = GridBagConstraints.NONE;     // compact, fixed width
        fld.anchor = GridBagConstraints.LINE_START;
        fld.insets = insets;
        fld.weightx = 0.0;

        int row = 0;
        // Paid out
        paidFilterLabel = new JLabel();
        lbl.gridy = row; panel.add(paidFilterLabel, lbl);
        paidFilterDropdown = new JComboBox<>();
        fld.gridy = row; panel.add(paidFilterDropdown, fld);
        row++;

        // Seller ID
        sellerFilterLabel = new JLabel();
        lbl.gridy = row; panel.add(sellerFilterLabel, lbl);
        sellerFilterDropdown = new JComboBox<>();
        fld.gridy = row; panel.add(sellerFilterDropdown, fld);
        row++;

        // Payment method
        paymentFilterLabel = new JLabel();
        lbl.gridy = row; panel.add(paymentFilterLabel, lbl);
        paymentTypeFilterDropdown = new JComboBox<>();
        fld.gridy = row; panel.add(paymentTypeFilterDropdown, fld);
        row++;

        paidFilterDropdown.addActionListener(e -> controller.filterUpdated());
        sellerFilterDropdown.addActionListener(e -> controller.filterUpdated());
        paymentTypeFilterDropdown.addActionListener(e -> controller.filterUpdated());

        // Search (same constraints as other fields)
        searchLabel = new JLabel();
        lbl.gridy = row; panel.add(searchLabel, lbl);

        GridBagConstraints sFld = new GridBagConstraints();
        sFld.gridx = 1; sFld.gridy = row; sFld.weightx = 0.0;
        sFld.fill = GridBagConstraints.NONE; sFld.insets = insets;
        searchField = new JTextField(20);
        searchLabel.setLabelFor(searchField);
        panel.add(searchField, sFld);

        // Synchronize a common width for all filter fields (combos + search)
        syncFilterFieldWidths();

        // Live RowFilter over current table contents (non-destructive; complements controller filtering)
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void update() {
                if (sorter == null) return;
                String q = searchField.getText();
                if (q == null || q.isBlank()) {
                    sorter.setRowFilter(null);
                } else {
                    final String needle = q.trim().toLowerCase(java.util.Locale.ROOT);
                    sorter.setRowFilter(new RowFilter<DefaultTableModel, Integer>() {
                        @Override public boolean include(Entry<? extends DefaultTableModel, ? extends Integer> entry) {
                            for (int i = 0; i < entry.getValueCount(); i++) {
                                Object v = entry.getValue(i);
                                if (v != null && v.toString().toLowerCase(java.util.Locale.ROOT).contains(needle)) {
                                    return true;
                                }
                            }
                            return false;
                        }
                    });
                }
            }
            @Override public void insertUpdate(DocumentEvent e) { update(); }
            @Override public void removeUpdate(DocumentEvent e) { update(); }
            @Override public void changedUpdate(DocumentEvent e) { update(); }
        });
        return panel;
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
        paidFilterDropdown.setSelectedIndex(paidIndex >= 0 ? paidIndex : 0);
        paidFilterLabel.setText(LocalizationManager.tr("filter.paid"));

        int sellerIndex = sellerFilterDropdown.getSelectedIndex();
        DefaultComboBoxModel<String> sellerModel = new DefaultComboBoxModel<>();
        sellerModel.addElement(LocalizationManager.tr("filter.all"));
        ComboBoxModel<String> currentSellerModel = sellerFilterDropdown.getModel();
        for (int i = 1; i < currentSellerModel.getSize(); i++) {
            sellerModel.addElement(currentSellerModel.getElementAt(i));
        }
        sellerFilterDropdown.setModel(sellerModel);
        sellerFilterDropdown.setSelectedIndex(sellerIndex >= 0 ? sellerIndex : 0);
        sellerFilterLabel.setText(LocalizationManager.tr("filter.seller"));

        int paymentIndex = paymentTypeFilterDropdown.getSelectedIndex();
        paymentTypeFilterDropdown.setModel(new DefaultComboBoxModel<>(new String[]{
                LocalizationManager.tr("filter.all"),
                LocalizationManager.tr("payment.swish"),
                LocalizationManager.tr("payment.cash")
        }));
        paymentTypeFilterDropdown.setSelectedIndex(paymentIndex >= 0 ? paymentIndex : 0);
        paymentFilterLabel.setText(LocalizationManager.tr("filter.payment_method"));

        // Buttons
        eraseAllDataButton.setText(LocalizationManager.tr(BUTTON_ERASE));
        archiveFilteredButton.setText(LocalizationManager.tr("button.archive_filtered"));
        // Ensure the right-hand top button reflects current mode after language switch
        boolean isOffline = ConfigurationStore.OFFLINE_EVENT_BOOL.getBooleanValueOrDefault(false);
        if (isOffline) {
            importDataButton.setText(LocalizationManager.tr(BUTTON_IMPORT)); // e.g., "Import register"
        } else {
            // Online: show "Update from Web". If the key is ever missing, fall back gracefully.
            String online = LocalizationManager.tr("history.update_from_web");
            // Many LocalizationManager implementations return the key itself when missing.
            if (online == null || online.startsWith("history.")) {
                online = LocalizationManager.tr(BUTTON_IMPORT); // fallback to a valid, localized label
            }
            importDataButton.setText(online);
        }
        // Visual: treat "Update from Web" as primary action
        importDataButton.putClientProperty("JButton.buttonType", "default");

        payoutButton.setText(LocalizationManager.tr(BUTTON_PAY_OUT));
        toClipboardButton.setText(LocalizationManager.tr(BUTTON_COPY_TO_CLIPBOARD));

        // Summary labels
        itemsCountLabel.setText(LocalizationManager.tr("history.items_count", itemsCount));
        totalSumLabel.setText(LocalizationManager.tr("history.total_sum", totalSum));
        // Emphasize the amount visually
        totalSumLabel.setFont(totalSumLabel.getFont().deriveFont(Font.BOLD));

        // Localize the Search label (now kept as a member reference)
        if (searchLabel != null) {
            searchLabel.setText(LocalizationManager.tr("filter.search"));
            if (searchField != null) searchLabel.setLabelFor(searchField);
        }
        // Re-sync widths after locale change so selected values remain visible
        syncFilterFieldWidths();
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

    /**
     * Make all filter controls the same width and aligned relative to labels.
     * This keeps selected values fully visible and the search field visually consistent.
     */
    private void syncFilterFieldWidths() {
        if (paidFilterDropdown == null || sellerFilterDropdown == null || paymentTypeFilterDropdown == null) return;
        // Measure current preferred widths and choose a reasonable minimum
        int min = 180;
        Dimension d1 = paidFilterDropdown.getPreferredSize();
        Dimension d2 = sellerFilterDropdown.getPreferredSize();
        Dimension d3 = paymentTypeFilterDropdown.getPreferredSize();
        int width = Math.max(min, Math.max(d1.width, Math.max(d2.width, d3.width)));
        int height = Math.max(Math.max(d1.height, d2.height), d3.height);
        filterFieldSize = new Dimension(width, height);
        paidFilterDropdown.setPreferredSize(filterFieldSize);
        sellerFilterDropdown.setPreferredSize(filterFieldSize);
        paymentTypeFilterDropdown.setPreferredSize(filterFieldSize);
        if (searchField != null) {
            // Match the same width + line up under the others
            searchField.setPreferredSize(filterFieldSize);
            searchField.setMinimumSize(filterFieldSize);
        }
    }
}
