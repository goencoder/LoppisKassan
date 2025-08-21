package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.controller.CashierControllerInterface;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.localization.LocalizationAware;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import se.goencoder.loppiskassan.util.Money;
import se.goencoder.loppiskassan.util.PriceList;

import static se.goencoder.loppiskassan.ui.UserInterface.createButton;

/**
 * Represents the cashier tab in the application, allowing users to input transactions,
 * manage purchases, and perform checkout operations.
 */
public class CashierTabPanel extends JPanel implements CashierPanelInterface, LocalizationAware {

    // Components for the cashier table and input fields
    private JTable cashierTable;
    private JTextField sellerField, pricesField, payedCashField;
    private JLabel noItemsLabel, sumLabel;
    private JLabel sellerLabel, pricesLabel, paidLabel, changeLabel;
    private JLabel changeValueLabel;

    // Buttons for checkout actions
    private JButton cancelCheckoutButton, checkoutCashButton, checkoutSwishButton;

    private int itemsCount = 0;
    private int sumValue = 0;

    private final CashierControllerInterface controller;

    /**
     * Initializes the cashier tab panel with its components and controller setup.
     */
    public CashierTabPanel(CashierControllerInterface controller) {
        this.controller = controller;
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
        SoldItemsTableModel tableModel = new SoldItemsTableModel(columnNames);
        cashierTable = new JTable(tableModel);
        cashierTable.getColumnModel().getColumn(1).setCellRenderer(Renderers.rightAligned());
        cashierTable.removeColumn(cashierTable.getColumnModel().getColumn(2));

        tableModel.addTableModelListener(e -> updateSummary());

        cashierTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    int row = cashierTable.getSelectedRow();
                    if (row >= 0) {
                        String itemId = tableModel.getValueAt(row, 2).toString();
                        controller.deleteItem(itemId);
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
        payedCashField = new JTextField();
        changeValueLabel = new JLabel();

        // Keep the fields clean as the user types (no jumpy caret):
        // - seller: 3 digits max
        // - prices: digits & spaces (collapsed)
        // - paid:   up to 5 digits
        TextFilters.install(sellerField, new TextFilters.DigitsOnlyFilter(3));
        TextFilters.install(pricesField, new TextFilters.DigitsAndSpacesFilter(200));
        TextFilters.install(payedCashField, new TextFilters.DigitsOnlyFilter(5));

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

        // Add a key listener to calculate change dynamically (no extra sanitizing needed with filters)
        payedCashField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                int payedAmount = payedCashField.getText().isEmpty() ? 0 : Integer.parseInt(payedCashField.getText());
                controller.calculateChange(payedAmount);
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

        SoldItemsTableModel model = getTableModel();
        model.setColumnNames(new String[]{
                LocalizationManager.tr("cashier.table.seller"),
                LocalizationManager.tr("cashier.table.price"),
                LocalizationManager.tr("cashier.table.item_id")
        });
        if (cashierTable.getColumnModel().getColumnCount() > 2) {
            cashierTable.removeColumn(cashierTable.getColumnModel().getColumn(2));
        }
        updateSummary();
    }

    // ------------------------------------------------------------------------
    // Methods for interacting with the CashierControllerInterface
    // ------------------------------------------------------------------------

    private SoldItemsTableModel getTableModel() {
        return (SoldItemsTableModel) cashierTable.getModel();
    }

    private void updateSummary() {
        SoldItemsTableModel model = getTableModel();
        itemsCount = model.getRowCount();
        int total = 0;
        for (SoldItem item : model.getItems()) {
            total += item.getPrice();
        }
        sumValue = total;
        Locale locale = new Locale(LocalizationManager.getLanguage());
        noItemsLabel.setText(LocalizationManager.tr("cashier.no_items", itemsCount));
        sumLabel.setText(Money.formatAmount(sumValue, locale, LocalizationManager.tr("currency.sek")));
        enableCheckoutButtons(itemsCount > 0);
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
        getTableModel().addItem(item);
    }

    @Override
    public void setPaidAmount(int amount) {
        payedCashField.setText(String.valueOf(amount));
    }

    @Override
    public void setChange(int amount) {
        Locale locale = new Locale(LocalizationManager.getLanguage());
        changeValueLabel.setText(Money.formatAmount(amount, locale, LocalizationManager.tr("currency.sek")));
    }

    @Override
    public Map<Integer, Integer[]> getAndClearSellerPrices() {
        String seller = sellerField.getText();
        int sellerId;
        try {
            sellerId = Integer.parseInt(seller);
        } catch (NumberFormatException e) {
            Popup.warn("cashier.invalid_seller");
            return new HashMap<>();
        }

        if (!controller.isSellerApproved(sellerId)) {
            Popup.warn("cashier.seller_not_approved");
            return new HashMap<>();
        }

        java.util.List<Integer> prices;
        try {
            prices = PriceList.parse(pricesField.getText());
        } catch (NumberFormatException e) {
            Popup.warn("cashier.invalid_price");
            return new HashMap<>();
        }

        Map<Integer, Integer[]> sellerPrices = new HashMap<>();
        sellerPrices.put(sellerId, prices.toArray(new Integer[0]));

        sellerField.setText("");
        pricesField.setText("");

        return sellerPrices;
    }

    @Override
    public void clearView() {
        SoldItemsTableModel model = getTableModel();
        model.clear();
        sellerField.setText("");
        pricesField.setText("");
        payedCashField.setText("");
        changeValueLabel.setText("");
        sumValue = 0;
        itemsCount = 0;
        updateSummary();
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
