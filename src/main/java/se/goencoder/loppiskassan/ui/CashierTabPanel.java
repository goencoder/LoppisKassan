package se.goencoder.loppiskassan.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.controller.CashierControllerInterface;
import se.goencoder.loppiskassan.controller.CashierTabController;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.ui.Ui;


/**
 * Represents the cashier tab in the application, allowing users to input transactions,
 * manage purchases, and perform checkout operations.
 */
public class CashierTabPanel extends JPanel implements CashierPanelInterface, LocalizationAware {

    // Components for the cashier table and input fields
    private JTable cashierTable;
    private JTextField sellerField, pricesField, payedCashField, changeCashField;
    private JLabel noItemsLabel, sumLabel;
    private JLabel sellerLabel, pricesLabel, paidLabel, changeLabel;

    // Buttons for checkout actions
    private JButton cancelCheckoutButton, checkoutCashButton, checkoutSwishButton;

    private int itemsCount = 0;
    private int sumValue = 0;

    /**
     * Initializes the cashier tab panel with its components and controller setup.
     */
    public CashierTabPanel() {
        setLayout(new BorderLayout());

        // Initialize and add components
        initializeTable(); // Table for displaying transaction details
        JPanel inputPanel = initializeInputPanel(); // Input fields for seller and prices
        JPanel buttonPanel = initializeButtonPanel(); // Action buttons for checkout operations

        // Add components to the panel
        add(Ui.padded(new JScrollPane(cashierTable), Ui.SP_L), BorderLayout.CENTER);
        add(Ui.padded(inputPanel, Ui.SP_L), BorderLayout.NORTH);
        add(Ui.padded(buttonPanel, Ui.SP_L), BorderLayout.SOUTH);

        // Set up controller and actions
        CashierControllerInterface controller = CashierTabController.getInstance();
        controller.setupCheckoutSwishButtonAction(checkoutSwishButton);
        controller.setupCheckoutCashButtonAction(checkoutCashButton);
        controller.setupCancelCheckoutButtonAction(cancelCheckoutButton);
        controller.setupPricesTextFieldAction(pricesField);
        controller.registerView(this);

        // Disable checkout buttons initially
        enableCheckoutButtons(false);
        reloadTexts();
    }

    /**
     * Initializes the table for displaying transaction details.
     * The table includes hidden columns for internal data like item IDs.
     */
    private void initializeTable() {
        String[] columnNames = {
                LocalizationManager.tr("cashier.table.seller"),
                LocalizationManager.tr("cashier.table.price"),
                LocalizationManager.tr("cashier.table.item_id")
        };
        DefaultTableModel tableModel = new DefaultTableModel(null, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Prevent cell editing
            }
        };
        cashierTable = new JTable(tableModel);
        cashierTable.removeColumn(cashierTable.getColumnModel().getColumn(2)); // Hide "Item ID" column
        Ui.zebra(cashierTable);
        cashierTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        DefaultTableCellRenderer right = new DefaultTableCellRenderer();
        right.setHorizontalAlignment(SwingConstants.RIGHT);
        cashierTable.getColumnModel().getColumn(1).setCellRenderer(right);
        cashierTable.getTableHeader().setPreferredSize(new Dimension(0, 28));

        // Add a key listener for delete functionality
        cashierTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    int row = cashierTable.getSelectedRow();
                    if (row >= 0) {
                        String itemId = tableModel.getValueAt(row, 2).toString();
                        CashierControllerInterface controller = CashierTabController.getInstance();
                        controller.deleteItem(itemId); // Delete the item from the table
                    }
                }
            }
        });
    }

    /**
     * Creates and returns the panel containing input fields for seller and price information.
     *
     * @return Configured input panel.
     */
    private JPanel initializeInputPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(8, 12, 0, 12));

        sellerField = new JTextField();
        pricesField = new JTextField();
        payedCashField = new JTextField();
        changeCashField = new JTextField();
        changeCashField.setEditable(false);

        sellerLabel = new JLabel();
        pricesLabel = new JLabel();
        paidLabel   = new JLabel();
        changeLabel = new JLabel();

        Insets L = new Insets(6, 8, 6, 6);
        Insets F = new Insets(6, 6, 6, 8);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0; gbc.weightx = 0; gbc.anchor = GridBagConstraints.LINE_END; gbc.insets = L;
        panel.add(sellerLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 0.5; gbc.anchor = GridBagConstraints.LINE_START; gbc.insets = F;
        panel.add(sellerField, gbc);

        gbc.gridx = 2; gbc.weightx = 0; gbc.anchor = GridBagConstraints.LINE_END; gbc.insets = L;
        panel.add(pricesLabel, gbc);
        gbc.gridx = 3; gbc.weightx = 0.5; gbc.anchor = GridBagConstraints.LINE_START; gbc.insets = F;
        panel.add(pricesField, gbc);

        gbc.gridy = 1;
        gbc.gridx = 0; gbc.weightx = 0; gbc.anchor = GridBagConstraints.LINE_END; gbc.insets = L;
        panel.add(paidLabel, gbc);
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.weightx = 1.0; gbc.anchor = GridBagConstraints.LINE_START; gbc.insets = F;
        panel.add(payedCashField, gbc);
        gbc.gridwidth = 1;

        gbc.gridy = 2;
        gbc.gridx = 0; gbc.weightx = 0; gbc.anchor = GridBagConstraints.LINE_END; gbc.insets = L;
        panel.add(changeLabel, gbc);
        gbc.gridx = 1; gbc.gridwidth = 3; gbc.weightx = 1.0; gbc.anchor = GridBagConstraints.LINE_START; gbc.insets = F;
        panel.add(changeCashField, gbc);
        gbc.gridwidth = 1;

        gbc.gridy = 3; gbc.gridx = 0; gbc.gridwidth = 4; gbc.weightx = 1.0; gbc.insets = new Insets(4, 8, 0, 8);
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        noItemsLabel = new JLabel();
        sumLabel = new JLabel();
        infoPanel.add(noItemsLabel);
        infoPanel.add(sumLabel);
        panel.add(infoPanel, gbc);

        CashierControllerInterface controller = CashierTabController.getInstance();
        payedCashField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                try {
                    int payedAmount = Integer.parseInt(payedCashField.getText());
                    controller.calculateChange(payedAmount);
                } catch (NumberFormatException ex) { /* ignore */ }
            }
        });

        return panel;
    }

    /**
     * Creates and returns the panel containing action buttons for checkout operations.
     *
     * @return Configured button panel.
     */
    private JPanel initializeButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, Ui.SP_M, 0));

        // Initialize buttons
        cancelCheckoutButton = new JButton();
        cancelCheckoutButton.putClientProperty("JButton.buttonType", "help");
        checkoutCashButton = new JButton();
        Ui.makePrimary(checkoutCashButton);
        checkoutSwishButton = new JButton();
        Ui.makePrimary(checkoutSwishButton);

        // Add buttons to the panel
        panel.add(cancelCheckoutButton);
        panel.add(checkoutCashButton);
        panel.add(checkoutSwishButton);

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
        sellerLabel.setText(LocalizationManager.tr("cashier.seller_id"));
        pricesLabel.setText(LocalizationManager.tr("cashier.prices_example"));
        paidLabel.setText(LocalizationManager.tr("cashier.paid"));
        changeLabel.setText(LocalizationManager.tr("cashier.change"));

        cancelCheckoutButton.setText(LocalizationManager.tr("cashier.cancel_purchase"));
        checkoutCashButton.setText(LocalizationManager.tr("cashier.cash"));
        checkoutSwishButton.setText(LocalizationManager.tr("cashier.swish"));

        DefaultTableModel model = (DefaultTableModel) cashierTable.getModel();
        model.setColumnIdentifiers(new String[]{
                LocalizationManager.tr("cashier.table.seller"),
                LocalizationManager.tr("cashier.table.price"),
                LocalizationManager.tr("cashier.table.item_id")
        });
        if (cashierTable.getColumnModel().getColumnCount() > 2) {
            cashierTable.removeColumn(cashierTable.getColumnModel().getColumn(2));
        }

        noItemsLabel.setText(LocalizationManager.tr("cashier.no_items", itemsCount));
        sumLabel.setText(LocalizationManager.tr("cashier.sum", sumValue));
    }

    // ------------------------------------------------------------------------
    // Methods for interacting with the CashierControllerInterface
    // ------------------------------------------------------------------------

    private DefaultTableModel getTableModel() {
        return (DefaultTableModel) cashierTable.getModel();
    }

    @Override
    public void setFocusToSellerField() {
        sellerField.requestFocus();
    }

    @Override
    public void enableCheckoutButtons(boolean enable) {
        checkoutCashButton.setEnabled(enable);
        checkoutSwishButton.setEnabled(enable);
        cancelCheckoutButton.setEnabled(enable);
    }

    @Override
    public void addSoldItem(SoldItem item) {
        DefaultTableModel model = getTableModel();
        model.insertRow(0, new Object[]{item.getSeller(), item.getPrice(), item.getItemId()});
    }

    @Override
    public void updateSumLabel(String newText) {
        sumValue = Integer.parseInt(newText);
        sumLabel.setText(LocalizationManager.tr("cashier.sum", sumValue));
    }

    @Override
    public void updateNoItemsLabel(String newText) {
        itemsCount = Integer.parseInt(newText);
        noItemsLabel.setText(LocalizationManager.tr("cashier.no_items", itemsCount));
    }

    @Override
    public void updatePayedCashField(Integer amount) {
        payedCashField.setText(amount.toString());
    }

    @Override
    public void updateChangeCashField(Integer amount) {
        changeCashField.setText(amount.toString());
    }

    @Override
    public Map<Integer, Integer[]> getAndClearSellerPrices() {
        String seller = sellerField.getText();
        int sellerId;

        try {
            sellerId = Integer.parseInt(seller);
        } catch (NumberFormatException e) {
            Popup.WARNING.showAndWait(
                    LocalizationManager.tr("cashier.invalid_seller.title"),
                    LocalizationManager.tr("cashier.invalid_seller.message"));
            return new HashMap<>();
        }

        if (!CashierTabController.getInstance().isSellerApproved(sellerId)) {
            Popup.WARNING.showAndWait(
                    LocalizationManager.tr("cashier.seller_not_approved.title"),
                    LocalizationManager.tr("cashier.seller_not_approved.message"));
            return new HashMap<>();
        }

        // Parse prices
        String[] priceStrings = pricesField.getText().split("\\s+");
        Integer[] priceInts = new Integer[priceStrings.length];
        try {
            for (int i = 0; i < priceStrings.length; i++) {
                priceInts[i] = Integer.parseInt(priceStrings[i]);
            }
        } catch (NumberFormatException e) {
            Popup.WARNING.showAndWait(
                    LocalizationManager.tr("cashier.invalid_price.title"),
                    LocalizationManager.tr("cashier.invalid_price.message"));
            return new HashMap<>();
        }

        // Return seller-prices mapping
        Map<Integer, Integer[]> sellerPrices = new HashMap<>();
        sellerPrices.put(sellerId, priceInts);

        // Clear input fields
        sellerField.setText("");
        pricesField.setText("");

        return sellerPrices;
    }

    @Override
    public void clearView() {
        DefaultTableModel model = getTableModel();
        model.setRowCount(0); // Clear table
        sellerField.setText("");
        pricesField.setText("");
        payedCashField.setText("");
        changeCashField.setText("");
        sumValue = 0;
        itemsCount = 0;
        sumLabel.setText(LocalizationManager.tr("cashier.sum", sumValue));
        noItemsLabel.setText(LocalizationManager.tr("cashier.no_items", itemsCount));
        setFocusToSellerField();
    }

    @Override
    public void selected() {
        // No-op for this panel
    }

    @Override
    public Component getComponent() {
        return this;
    }
}
