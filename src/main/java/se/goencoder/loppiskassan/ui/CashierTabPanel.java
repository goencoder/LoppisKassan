package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.controller.CashierControllerInterface;
import se.goencoder.loppiskassan.controller.CashierTabController;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.localization.LocalizationAware;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;

import static se.goencoder.loppiskassan.ui.UserInterface.createButton;

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
    // New: visible label for change, while keeping the hidden text field for controller updates
    private JLabel changeValueLabel;

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
        add(new JScrollPane(cashierTable), BorderLayout.CENTER); // ScrollPane wraps the table
        add(inputPanel, BorderLayout.NORTH); // Input panel at the top
        add(buttonPanel, BorderLayout.SOUTH); // Button panel at the bottom

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
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.Y_AXIS));
        // Add some breathing room between tabs and the first row of inputs
        inputPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 12, 0, 12));

        // --- init fields ---
        sellerField = new JTextField();
        pricesField = new JTextField();
        payedCashField = new JTextField();      // stays editable
        changeCashField = new JTextField();     // kept for controller; will be hidden and mirrored to a JLabel
        changeCashField.setEditable(false);

        // --- init labels ---
        sellerLabel = new JLabel();
        pricesLabel = new JLabel();
        paidLabel   = new JLabel();
        changeLabel = new JLabel();
        changeValueLabel = new JLabel();        // visible "Växel"-value

        // ===== TOP ROW: labels tight to fields (like Historik) =====
        JPanel topRow = new JPanel(new BorderLayout());
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.add(sellerLabel);
        // Visual width: seller number ~3 digits
        sellerField.setColumns(3);
        left.add(sellerField);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        right.add(pricesLabel);
        // Visual width: make room for multiple prices like "50 100 50 15 20"
        pricesField.setColumns(22);
        right.add(pricesField);
        topRow.add(left, BorderLayout.WEST);
        topRow.add(right, BorderLayout.CENTER);
        inputPanel.add(topRow);

        // ===== INFO LINE: "items & sum • Betalt: [ ] • Växel: <value>" =====
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        noItemsLabel = new JLabel();
        sumLabel = new JLabel();
        // Make the total (e.g., "629 SEK") stand out for the cashier
        sumLabel.setFont(sumLabel.getFont().deriveFont(java.awt.Font.BOLD));
        infoPanel.add(noItemsLabel);
        infoPanel.add(sumLabel);
        // Betalt inline (editable)
        infoPanel.add(paidLabel);
        // Visual width: ~5 digits
        payedCashField.setColumns(5);
        infoPanel.add(payedCashField);
        // Växel inline (read-only, as text)
        infoPanel.add(changeLabel);
        infoPanel.add(changeValueLabel);
        inputPanel.add(infoPanel);

        // We keep changeCashField but DON'T add it to the layout.
        // Mirror its text to the visible changeValueLabel so existing controller code keeps working.
        changeCashField.getDocument().addDocumentListener(new DocumentListener() {
            private void sync() { changeValueLabel.setText(changeCashField.getText()); }
            @Override public void insertUpdate(DocumentEvent e) { sync(); }
            @Override public void removeUpdate(DocumentEvent e) { sync(); }
            @Override public void changedUpdate(DocumentEvent e) { sync(); }
        });

        // Add a key listener to calculate change dynamically
        payedCashField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                CashierControllerInterface controller = CashierTabController.getInstance();
                payedCashField.setText(payedCashField.getText().replaceAll("[^0-9]", ""));
                try {
                    int payedAmount = payedCashField.getText().isEmpty() ? 0 : Integer.parseInt(payedCashField.getText());
                    controller.calculateChange(payedAmount);
                } catch (NumberFormatException ex) {
                    Popup.WARNING.showAndWait(
                            LocalizationManager.tr("cashier.invalid_amount.title"),
                            LocalizationManager.tr("cashier.invalid_amount.message"));
                }
            }
        });


        return inputPanel;
    }

    /**
     * Creates and returns the panel containing action buttons for checkout operations.
     *
     * @return Configured button panel.
     */
    private JPanel initializeButtonPanel() {
        JPanel buttonPanel = new JPanel(new GridLayout(1, 3));

        // Initialize buttons
        cancelCheckoutButton = createButton("", 150, 50);
        checkoutCashButton = createButton("", 150, 50);
        checkoutSwishButton = createButton("", 150, 50);

        // Add buttons to the panel
        buttonPanel.add(cancelCheckoutButton);
        buttonPanel.add(checkoutCashButton);
        buttonPanel.add(checkoutSwishButton);

        return buttonPanel;
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
