package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.PaymentMethod;
import se.goencoder.loppiskassan.controller.HistoryControllerInterface;
import se.goencoder.loppiskassan.controller.HistoryTabController;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.ui.Ui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Set;

import static se.goencoder.loppiskassan.ui.Constants.*;

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
        setLayout(new BorderLayout());
        LocalizationManager.addListener(this::reloadTexts);

        JPanel filterPanel = buildFilterPanel();
        add(Ui.padded(filterPanel, Ui.SP_L), BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(initializeSummaryPanel(), BorderLayout.NORTH);
        centerPanel.add(new JScrollPane(initializeTable()), BorderLayout.CENTER);
        add(Ui.padded(centerPanel, Ui.SP_L), BorderLayout.CENTER);

        add(buildActionStack(), BorderLayout.EAST);
        add(Ui.padded(initializeFooterPanel(), Ui.SP_L), BorderLayout.SOUTH);

        controller.registerView(this);
        reloadTexts();
    }

    /**
     * Creates and initializes the history table for displaying sold items.
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

        historyTable = new JTable(model);
        Ui.zebra(historyTable);
        historyTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        historyTable.getColumnModel().getColumn(1).setCellRenderer(right);
        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        historyTable.getColumnModel().getColumn(2).setCellRenderer(center);
        return historyTable;
    }

    private JPanel buildFilterPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        Insets insets = new Insets(Ui.SP_XS, Ui.SP_L, Ui.SP_XS, Ui.SP_L);

        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.anchor = GridBagConstraints.LINE_END;
        labelGbc.insets = insets;

        GridBagConstraints fieldGbc = new GridBagConstraints();
        fieldGbc.fill = GridBagConstraints.HORIZONTAL;
        fieldGbc.weightx = 1.0;
        fieldGbc.insets = insets;

        int col = 0;
        paidFilterLabel = new JLabel();
        labelGbc.gridx = col++; labelGbc.gridy = 0; panel.add(paidFilterLabel, labelGbc);
        paidFilterDropdown = new JComboBox<>();
        fieldGbc.gridx = col++; fieldGbc.gridy = 0; panel.add(paidFilterDropdown, fieldGbc);

        sellerFilterLabel = new JLabel();
        labelGbc.gridx = col++; panel.add(sellerFilterLabel, labelGbc);
        sellerFilterDropdown = new JComboBox<>();
        fieldGbc.gridx = col++; panel.add(sellerFilterDropdown, fieldGbc);

        paymentFilterLabel = new JLabel();
        labelGbc.gridx = col++; panel.add(paymentFilterLabel, labelGbc);
        paymentTypeFilterDropdown = new JComboBox<>();
        fieldGbc.gridx = col++; fieldGbc.gridwidth = GridBagConstraints.REMAINDER; panel.add(paymentTypeFilterDropdown, fieldGbc);

        paidFilterDropdown.addActionListener(e -> controller.filterUpdated());
        sellerFilterDropdown.addActionListener(e -> controller.filterUpdated());
        paymentTypeFilterDropdown.addActionListener(e -> controller.filterUpdated());

        return panel;
    }


    /**
     * Creates the summary panel to display total items and total price.
     *
     * @return The summary panel.
     */
    private JPanel initializeSummaryPanel() {
        JPanel summaryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, Ui.SP_S, 0));
        itemsCountLabel = new JLabel();
        totalSumLabel = new JLabel();
        summaryPanel.add(itemsCountLabel);
        summaryPanel.add(totalSumLabel);
        return summaryPanel;
    }

    private JPanel buildActionStack() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        eraseAllDataButton = new JButton();
        archiveFilteredButton = new JButton();
        importDataButton = new JButton();

        eraseAllDataButton.addActionListener(e -> controller.buttonAction(BUTTON_ERASE));
        archiveFilteredButton.addActionListener(e -> controller.buttonAction(BUTTON_ARCHIVE));
        importDataButton.addActionListener(e -> controller.buttonAction(BUTTON_IMPORT));

        panel.add(eraseAllDataButton);
        panel.add(Box.createVerticalStrut(Ui.SP_S));
        panel.add(archiveFilteredButton);
        panel.add(Box.createVerticalStrut(Ui.SP_S));
        panel.add(importDataButton);

        for (Component c : panel.getComponents()) {
            if (c instanceof JButton b) {
                b.setAlignmentX(Component.CENTER_ALIGNMENT);
            }
        }

        return Ui.padded(panel, Ui.SP_L);
    }

    private JPanel initializeFooterPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, Ui.SP_M, 0));
        payoutButton = new JButton();
        Ui.makePrimary(payoutButton);
        toClipboardButton = new JButton();
        panel.add(payoutButton);
        panel.add(toClipboardButton);
        payoutButton.addActionListener(e -> controller.buttonAction(BUTTON_PAY_OUT));
        toClipboardButton.addActionListener(e -> controller.buttonAction(BUTTON_COPY_TO_CLIPBOARD));
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

        int width = Math.max(eraseAllDataButton.getPreferredSize().width,
                Math.max(archiveFilteredButton.getPreferredSize().width, importDataButton.getPreferredSize().width));
        int height = eraseAllDataButton.getPreferredSize().height;
        Dimension size = new Dimension(width, height);
        eraseAllDataButton.setMaximumSize(size);
        archiveFilteredButton.setMaximumSize(size);
        importDataButton.setMaximumSize(size);

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
