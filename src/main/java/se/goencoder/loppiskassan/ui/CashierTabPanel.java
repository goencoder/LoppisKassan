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
 * Represents the cashier tab in the application, allowing for transaction input and display.
 */
public class CashierTabPanel extends JPanel implements CashierPanelInterface{
    private JTable cashierTable;
    private JTextField sellerField, pricesField, payedCashField, changeCashField;
    private JLabel noItemsLabel, sumLabel;

    private JButton cancelCheckoutButton, checkoutCashButton, checkoutSwishButton;


    /**
     * Initializes the cashier tab panel with its components.
     */
    public CashierTabPanel() {
        // Use BorderLayout to organize components vertically and horizontally
        setLayout(new BorderLayout());

        // Initialize and configure the table for displaying transaction details
        initializeTable();

        // Initialize and configure the input panel for transaction inputs
        JPanel inputPanel = initializeInputPanel();

        // Initialize and configure the button panel for action buttons
        JPanel buttonPanel = initializeButtonPanel();

        // Add the table, input panel, and button panel to the main panel
        add(new JScrollPane(cashierTable), BorderLayout.CENTER); // ScrollPane for table
        add(inputPanel, BorderLayout.NORTH); // Input panel at the top
        add(buttonPanel, BorderLayout.SOUTH); // Button panel at the bottom
        CashierControllerInterface controller = CashierTabController.getInstance();
        controller.setupCheckoutSwishButtonAction(checkoutSwishButton);
        controller.setupCheckoutCashButtonAction(checkoutCashButton);
        controller.setupCancelCheckoutButtonAction(cancelCheckoutButton);
        controller.setupPricesTextFieldAction(pricesField);
        controller.registerView(this);
        enableCheckoutButtons(false);

    }
    // Inside CashierTabPanel class


    /**
     * Initializes the table component used to display transactions.
     */
    private void initializeTable() {
        // Add an extra column for the item ID but do not include it in the column names to be displayed
        String[] columnNames = {"Säljare", "Pris", "Item ID"}; // The "Item ID" column will be hidden
        DefaultTableModel tableModel = new DefaultTableModel(null, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                // Make table cells non-editable
                return false;
            }
        };
        cashierTable = new JTable(tableModel);

        // Hide the "Item ID" column from view but keep it in the model
        cashierTable.removeColumn(cashierTable.getColumnModel().getColumn(2));
        cashierTable.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_DELETE) {
                    int row = cashierTable.getSelectedRow();
                    if (row >= 0) {
                        DefaultTableModel model = (DefaultTableModel) cashierTable.getModel();
                        // Assuming the item ID is stored in the third column (hidden), which is index 2
                        String itemId = model.getValueAt(row, 2).toString();
                        CashierControllerInterface controller = CashierTabController.getInstance();
                        controller.deleteItem(itemId);
                    }
                }
            }
        });
    }


    /**
     * Initializes and returns the input panel for transaction inputs.
     * @return The configured input panel.
     */
    private JPanel initializeInputPanel() {
        // Panel using BoxLayout for vertical stacking of components
        JPanel inputPanel = new JPanel();
        inputPanel.setLayout(new BoxLayout(inputPanel, BoxLayout.Y_AXIS));

        // Fields panel for input fields, using GridLayout for a form-like structure
        JPanel fieldsPanel = new JPanel(new GridLayout(0, 2));

        // Initialize input fields
        sellerField = new JTextField();
        pricesField = new JTextField();
        payedCashField = new JTextField();
        changeCashField = new JTextField();
        changeCashField.setEditable(false); // Change field should not be editable

        // Add labels and fields to the fields panel
        fieldsPanel.add(new JLabel("Säljnummer:"));
        fieldsPanel.add(sellerField);
        fieldsPanel.add(new JLabel("Pris(er) ex: 10 150"));
        fieldsPanel.add(pricesField);
        fieldsPanel.add(new JLabel("Betalt:"));
        fieldsPanel.add(payedCashField);
        fieldsPanel.add(new JLabel("Växel:"));
        fieldsPanel.add(changeCashField);

        payedCashField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent e) {
                CashierControllerInterface controller = CashierTabController.getInstance();
                payedCashField.setText(payedCashField.getText().replaceAll("[^0-9]", ""));
                int payedAmount = 0;
                try {
                    if (!payedCashField.getText().isEmpty()){
                        payedAmount = Integer.parseInt(payedCashField.getText());
                    }
                    controller.calculateChange(payedAmount);
                } catch (NumberFormatException ex) {
                    Popup.WARNING.showAndWait("Felaktigt belopp", "Ange ett korrekt belopp");
                }
            }
        });
        // Add fields panel to the input panel
        inputPanel.add(fieldsPanel);

        // Info panel for displaying additional transaction information
        JPanel infoPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        noItemsLabel = new JLabel("0 varor");
        sumLabel = new JLabel("0 SEK");
        infoPanel.add(noItemsLabel);
        infoPanel.add(sumLabel);

        // Add info panel to the input panel
        inputPanel.add(infoPanel);

        return inputPanel;
    }

    /**
     * Initializes and returns the button panel for cashier actions.
     * @return The configured button panel.
     */
    private JPanel initializeButtonPanel() {
        // Panel using GridLayout for evenly spaced buttons
        JPanel buttonPanel = new JPanel(new GridLayout(1, 3));

        // Initialize buttons
        cancelCheckoutButton = createButton("Avbryt köp", 150, 50);
        checkoutCashButton = createButton("Kontant", 150,50);
        checkoutSwishButton = createButton("Swish", 150,50);

        // Add buttons to the panel
        buttonPanel.add(cancelCheckoutButton);
        buttonPanel.add(checkoutCashButton);
        buttonPanel.add(checkoutSwishButton);

        return buttonPanel;
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
        // if parsing fails, open a warning popup and do nothing
        String seller = this.sellerField.getText();
        int sellerId;
        // attempt to parse seller
        try {
            sellerId = Integer.parseInt(seller);
        } catch (NumberFormatException e) {
            Popup.WARNING.showAndWait("Felaktigt säljnummer", "Säljnummer måste vara ett heltal");
            return null;
        }
        if (!CashierTabController.getInstance().isSellerApproved(sellerId)) {
            Popup.WARNING.showAndWait("Säljare ej godkänd", "Säljaren är inte godkänd för detta event");
            return null;
        }
        // attempt to parse prices. The prices are separated by space (or any whitespace characters), so we split first and then parse
        String prices = this.pricesField.getText();
        String[] priceStrings = prices.split("\\s+");
        Integer[] priceInts = new Integer[priceStrings.length];
        for (int i = 0; i < priceStrings.length; i++) {
            try {
                priceInts[i] = Integer.parseInt(priceStrings[i]);
            } catch (NumberFormatException e) {
                Popup.WARNING.showAndWait("Felaktigt pris", "Pris ("+priceStrings[i]+") måste vara ett heltal: "+ prices);
                return null;
            }
        }

        // create the map and return it
        Map<Integer, Integer[]> sellerPrices = new HashMap<>();
        sellerPrices.put(sellerId, priceInts);
        // clear the fields
        this.sellerField.setText("");
        this.pricesField.setText("");
        return sellerPrices;
    }

    @Override
    public void clearView() {
        // Clear all tables, text fields, set focus to sellerField
        DefaultTableModel model = (DefaultTableModel) cashierTable.getModel();
        model.setRowCount(0);
        sellerField.setText("");
        pricesField.setText("");
        payedCashField.setText("");
        changeCashField.setText("");
        sellerField.requestFocus();
        sumLabel.setText("0 SEK");
        noItemsLabel.setText("0 varor");


    }

    @Override
    public void selected() {
        // NOOP
    }
}
