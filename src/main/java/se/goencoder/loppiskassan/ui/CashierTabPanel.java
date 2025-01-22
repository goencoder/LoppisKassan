package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.SoldItem;
import se.goencoder.loppiskassan.controller.CashierControllerInterface;
import se.goencoder.loppiskassan.controller.CashierTabController;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
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
public class CashierTabPanel extends JPanel implements CashierPanelInterface {

    // Components for the cashier table and input fields
    private JTable cashierTable;
    private JTextField sellerField, pricesField, payedCashField, changeCashField;
    private JLabel noItemsLabel, sumLabel;

    // Buttons for checkout actions
    private JButton cancelCheckoutButton, checkoutCashButton, checkoutSwishButton;

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
    }

    /**
     * Initializes the table for displaying transaction details.
     * The table includes hidden columns for internal data like item IDs.
     */
    private void initializeTable() {
        String[] columnNames = {"Säljare", "Pris", "Item ID"};
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

        // Fields panel with GridLayout for structured input fields
        JPanel fieldsPanel = new JPanel(new GridLayout(0, 2));

        // Initialize input fields
        sellerField = new JTextField();
        pricesField = new JTextField();
        payedCashField = new JTextField();
        changeCashField = new JTextField();
        changeCashField.setEditable(false); // Change field should not be editable

        // Add labels and fields to the panel
        fieldsPanel.add(new JLabel("Säljnummer:"));
        fieldsPanel.add(sellerField);
        fieldsPanel.add(new JLabel("Pris(er) ex: 10 150"));
        fieldsPanel.add(pricesField);
        fieldsPanel.add(new JLabel("Betalt:"));
        fieldsPanel.add(payedCashField);
        fieldsPanel.add(new JLabel("Växel:"));
        fieldsPanel.add(changeCashField);

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
                    Popup.WARNING.showAndWait("Felaktigt belopp", "Ange ett korrekt belopp");
                }
            }
        });

        inputPanel.add(fieldsPanel);

        // Info panel for displaying additional details
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        noItemsLabel = new JLabel("0 varor");
        sumLabel = new JLabel("0 SEK");
        infoPanel.add(noItemsLabel);
        infoPanel.add(sumLabel);

        inputPanel.add(infoPanel);

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
        cancelCheckoutButton = createButton("Avbryt köp", 150, 50);
        checkoutCashButton = createButton("Kontant", 150, 50);
        checkoutSwishButton = createButton("Swish", 150, 50);

        // Add buttons to the panel
        buttonPanel.add(cancelCheckoutButton);
        buttonPanel.add(checkoutCashButton);
        buttonPanel.add(checkoutSwishButton);

        return buttonPanel;
    }

    // ------------------------------------------------------------------------
    // Methods for interacting with the CashierControllerInterface
    // ------------------------------------------------------------------------

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
        DefaultTableModel model = (DefaultTableModel) cashierTable.getModel();
        model.insertRow(0, new Object[]{item.getSeller(), item.getPrice(), item.getItemId()});
    }

    @Override
    public void updateSumLabel(String newText) {
        sumLabel.setText(newText + " SEK");
    }

    @Override
    public void updateNoItemsLabel(String newText) {
        noItemsLabel.setText(newText + " varor");
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
            Popup.WARNING.showAndWait("Felaktigt säljnummer", "Säljnummer måste vara ett heltal");
            return new HashMap<>();
        }

        if (!CashierTabController.getInstance().isSellerApproved(sellerId)) {
            Popup.WARNING.showAndWait("Säljare ej godkänd", "Säljaren är inte godkänd för detta event");
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
            Popup.WARNING.showAndWait("Felaktigt pris", "Ange korrekta heltal för priser");
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
        DefaultTableModel model = (DefaultTableModel) cashierTable.getModel();
        model.setRowCount(0); // Clear table
        sellerField.setText("");
        pricesField.setText("");
        payedCashField.setText("");
        changeCashField.setText("");
        sumLabel.setText("0 SEK");
        noItemsLabel.setText("0 varor");
        setFocusToSellerField();
    }

    @Override
    public void selected() {
        // No-op for this panel
    }
}
