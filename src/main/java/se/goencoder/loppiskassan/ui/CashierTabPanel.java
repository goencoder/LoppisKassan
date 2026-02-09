package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;
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

/**
 * Represents the cashier tab in the application, allowing users to input transactions,
 * manage purchases, and perform checkout operations.
 */
public class CashierTabPanel extends JPanel implements CashierPanelInterface, LocalizationAware, SelectabableTab {

    // Components for the cashier table and input fields
    private JTable cashierTable;
    private JTextField sellerField, pricesField, payedCashField;
    private JLabel noItemsLabel, sumLabel;
    private JLabel sellerLabel, pricesLabel, paidLabel, changeLabel;
    private JLabel changeValueLabel;
    private JLabel totalAmountLabel;
    private JLabel totalItemCountLabel;
    private JPanel emptyStatePanel;
    private JScrollPane tableScrollPane;
    private JPanel cartPanel; // Varukorg-container

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
        setBackground(AppColors.WHITE);

        // Initialize and add components
        initializeTable(); // Table for displaying transaction details
        JPanel inputPanel = initializeInputPanel(); // Input fields for seller and prices
        JPanel totalPanel = createTotalPanel(); // Right panel with large total display
        JPanel buttonPanel = initializeButtonPanel(); // Action buttons for checkout operations

        // Create cart panel (table + empty state)
        cartPanel = new JPanel(new CardLayout());
        cartPanel.setBackground(AppColors.WHITE);
        tableScrollPane = new JScrollPane(cashierTable);
        tableScrollPane.setBorder(BorderFactory.createLineBorder(AppColors.BORDER, 1));
        tableScrollPane.getViewport().setBackground(AppColors.WHITE);
        cashierTable.setBackground(AppColors.WHITE);
        cashierTable.setFillsViewportHeight(true);
        emptyStatePanel = createEmptyStatePanel();
        cartPanel.add(tableScrollPane, "table");
        cartPanel.add(emptyStatePanel, "empty");

        // Create split pane for center content (cart + total)
        JSplitPane centerSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, cartPanel, totalPanel);
        centerSplit.setResizeWeight(0.7); // 70% för varukorg, 30% för total
        centerSplit.setDividerLocation(0.7);
        centerSplit.setBackground(AppColors.WHITE);
        centerSplit.setDividerSize(1);
        centerSplit.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Add components to the panel
        add(inputPanel, BorderLayout.NORTH); // Input panel at the top
        add(centerSplit, BorderLayout.CENTER); // Split pane in center
        add(buttonPanel, BorderLayout.SOUTH); // Button panel at the bottom

        // Register view and set up local action listeners
        controller.registerView(this);
        setupActionListeners();

        // Disable checkout buttons initially
        enableCheckoutButtons(false);
        reloadTexts();
        
        // Show empty state initially
        showEmptyState(true);
    }

    /**
     * Sets up action listeners for UI components to call controller intent methods.
     */
    private void setupActionListeners() {
        checkoutSwishButton.addActionListener(e -> controller.onCheckout(V1PaymentMethod.Swish));
        checkoutCashButton.addActionListener(e -> controller.onCheckout(V1PaymentMethod.Kontant));
        cancelCheckoutButton.addActionListener(e -> controller.onCancelCheckout());
        pricesField.addActionListener(e -> controller.onPricesSubmitted());
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
                        String itemId = tableModel.getValueAt(row, SoldItemsTableModel.COLUMN_ITEM_ID).toString();
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
        inputPanel.setBackground(AppColors.WHITE);
        inputPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, AppColors.BORDER),
            BorderFactory.createEmptyBorder(16, 16, 16, 16)
        ));

        // --- init fields ---
        sellerField = new JTextField();
        pricesField = new JTextField();
        payedCashField = new JTextField();
        changeValueLabel = new JLabel();

        // Keep the fields clean as the user types (no jumpy caret):
        TextFilters.install(sellerField, new TextFilters.DigitsOnlyFilter(3));
        TextFilters.install(pricesField, new TextFilters.DigitsAndSpacesFilter(200));
        TextFilters.install(payedCashField, new TextFilters.DigitsOnlyFilter(5));

        // --- init labels ---
        sellerLabel = new JLabel();
        pricesLabel = new JLabel();
        paidLabel   = new JLabel();
        changeLabel = new JLabel();
        changeValueLabel = new JLabel();

        // ===== TOP ROW: Säljnr + Pris(er) =====
        JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 8));
        topRow.setOpaque(false);
        
        topRow.add(sellerLabel);
        sellerField.setPreferredSize(new Dimension(80, 32));
        topRow.add(sellerField);
        
        topRow.add(pricesLabel);
        pricesField.setPreferredSize(new Dimension(300, 32));
        topRow.add(pricesField);
        
        inputPanel.add(topRow);

        // ===== INSTRUCTIONS ROW =====
        JPanel instructionsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 4));
        instructionsPanel.setOpaque(false);
        JLabel instructionsLabel = new JLabel("Enter = Lägg till   Delete = Ta bort   Esc = Avbryt   F2 = Swish   F3 = Kontant");
        instructionsLabel.setFont(instructionsLabel.getFont().deriveFont(Font.PLAIN, 11f));
        instructionsLabel.setForeground(AppColors.TEXT_MUTED);
        instructionsPanel.add(instructionsLabel);
        inputPanel.add(instructionsPanel);

        return inputPanel;
    }

    /**
     * Creates empty state panel shown when cart is empty.
     */
    private JPanel createEmptyStatePanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(AppColors.WHITE);
        
        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setOpaque(false);
        
        JLabel iconLabel = new JLabel("🛒");
        iconLabel.setFont(iconLabel.getFont().deriveFont(48f));
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel textLabel = new JLabel("Skriv säljnummer och pris,");
        textLabel.setFont(textLabel.getFont().deriveFont(14f));
        textLabel.setForeground(AppColors.TEXT_MUTED);
        textLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel text2Label = new JLabel("tryck Enter för att lägga till");
        text2Label.setFont(text2Label.getFont().deriveFont(14f));
        text2Label.setForeground(AppColors.TEXT_MUTED);
        text2Label.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel shortcutsLabel = new JLabel("F2 = Swish  •  F3 = Kontant");
        shortcutsLabel.setFont(shortcutsLabel.getFont().deriveFont(Font.PLAIN, 12f));
        shortcutsLabel.setForeground(AppColors.TEXT_MUTED);
        shortcutsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        shortcutsLabel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        
        content.add(iconLabel);
        content.add(Box.createVerticalStrut(16));
        content.add(textLabel);
        content.add(text2Label);
        content.add(shortcutsLabel);
        
        panel.add(content);
        return panel;
    }

    /**
     * Creates the total panel (right side) with large typography.
     */
    private JPanel createTotalPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(AppColors.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 1, 0, 0, AppColors.BORDER),
            BorderFactory.createEmptyBorder(24, 24, 24, 24)
        ));
        
        // Title
        JLabel titleLabel = new JLabel("Totalt");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(titleLabel);
        panel.add(Box.createVerticalStrut(16));
        
        // Items count
        totalItemCountLabel = new JLabel("0 varor");
        totalItemCountLabel.setFont(totalItemCountLabel.getFont().deriveFont(Font.PLAIN, 13f));
        totalItemCountLabel.setForeground(AppColors.TEXT_MUTED);
        totalItemCountLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(totalItemCountLabel);
        panel.add(Box.createVerticalStrut(8));
        
        // Total amount (large)
        JLabel attBetalaLabel = new JLabel("Att betala:");
        attBetalaLabel.setFont(attBetalaLabel.getFont().deriveFont(Font.PLAIN, 13f));
        attBetalaLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(attBetalaLabel);
        
        totalAmountLabel = new JLabel("0 kr");
        totalAmountLabel.setFont(totalAmountLabel.getFont().deriveFont(Font.BOLD, 36f));
        totalAmountLabel.setForeground(AppColors.TEXT_PRIMARY);
        totalAmountLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(totalAmountLabel);
        
        panel.add(Box.createVerticalStrut(32));
        
        // Paid field
        paidLabel = new JLabel("Betalt:");
        paidLabel.setFont(paidLabel.getFont().deriveFont(Font.PLAIN, 13f));
        paidLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(paidLabel);
        panel.add(Box.createVerticalStrut(4));
        
        payedCashField = new JTextField();
        payedCashField.setPreferredSize(new Dimension(150, 36));
        payedCashField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        payedCashField.setAlignmentX(Component.LEFT_ALIGNMENT);
        payedCashField.setFont(payedCashField.getFont().deriveFont(Font.PLAIN, 16f));
        TextFilters.install(payedCashField, new TextFilters.DigitsOnlyFilter(5));
        panel.add(payedCashField);
        
        // Add a key listener to calculate change dynamically
        payedCashField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                int payedAmount = 0;
                String txt = payedCashField.getText();
                if (txt != null && !txt.isEmpty()) {
                    try {
                        payedAmount = Integer.parseInt(txt);
                    } catch (NumberFormatException ignore) {
                        payedAmount = 0;
                    }
                }
                controller.calculateChange(payedAmount);
            }
        });
        
        panel.add(Box.createVerticalStrut(16));
        
        // Change
        changeLabel = new JLabel("Växel:");
        changeLabel.setFont(changeLabel.getFont().deriveFont(Font.PLAIN, 13f));
        changeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(changeLabel);
        
        changeValueLabel = new JLabel("0 kr");
        changeValueLabel.setFont(changeValueLabel.getFont().deriveFont(Font.BOLD, 24f));
        changeValueLabel.setForeground(AppColors.SUCCESS); // Green for positive
        changeValueLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(changeValueLabel);
        
        panel.add(Box.createVerticalGlue());
        
        return panel;
    }

    /**
     * Shows or hides the empty state.
     */
    private void showEmptyState(boolean empty) {
        CardLayout layout = (CardLayout) cartPanel.getLayout();
        if (empty) {
            layout.show(cartPanel, "empty");
        } else {
            layout.show(cartPanel, "table");
        }
    }

    /**
     * Creates and returns the panel containing action buttons for checkout operations.
     *
     * @return Configured button panel.
     */
    private JPanel initializeButtonPanel() {
        JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 1, 0));
        buttonPanel.setBackground(AppColors.WHITE);
        buttonPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, AppColors.BORDER));

        // Initialize buttons
        cancelCheckoutButton = AppButton.create("", AppButton.Variant.GHOST, AppButton.Size.XLARGE);

        checkoutCashButton = AppButton.create("", AppButton.Variant.SECONDARY, AppButton.Size.XLARGE);

        checkoutSwishButton = AppButton.create("", AppButton.Variant.PRIMARY, AppButton.Size.XLARGE);

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
    public void selected() {
        // Called when tab becomes active - focus seller field
        SwingUtilities.invokeLater(this::setFocusToSellerField);
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
        checkoutCashButton.setText(LocalizationManager.tr("cashier.cash") + " (F3)");
        checkoutSwishButton.setText(LocalizationManager.tr("cashier.swish") + " (F2)");

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
        for (V1SoldItem item : model.getItems()) {
            total += item.getPrice();
        }
        sumValue = total;
        Locale locale = new Locale(LocalizationManager.getLanguage());
        
        // Update item count label
        totalItemCountLabel.setText(LocalizationManager.tr("cashier.no_items", itemsCount));
        
        // Update total amount (large display)
        totalAmountLabel.setText(Money.formatAmount(sumValue, locale, ""));
        
        // Show/hide empty state
        showEmptyState(itemsCount == 0);
        
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
    public void addSoldItem(V1SoldItem item) {
        getTableModel().addItem(item);
    }

    @Override
    public void setPaidAmount(int amount) {
        payedCashField.setText(String.valueOf(amount));
    }

    @Override
    public void setChange(int amount) {
        Locale locale = new Locale(LocalizationManager.getLanguage());
        changeValueLabel.setText(Money.formatAmount(amount, locale, ""));
        
        // Color code: green if >= 0, red if negative
        if (amount >= 0) {
            changeValueLabel.setForeground(AppColors.SUCCESS); // Green
        } else {
            changeValueLabel.setForeground(AppColors.DANGER); // Red
        }
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
    public Component getComponent() {
        return this;
    }
}
