package se.goencoder.loppiskassan.ui;

import se.goencoder.iloppis.model.Event;
import se.goencoder.loppiskassan.controller.DiscoveryControllerInterface;
import se.goencoder.loppiskassan.controller.DiscoveryTabController;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DiscoveryTabPanel extends JPanel implements DiscoveryPanelInterface {

    private final JTextField dateFromField;
    private final JButton discoverButton;
    private final JTable eventsTable;
    private final JTextField cashierCodeField;
    private final JButton getTokenButton;
    private final JLabel statusLabel;

    private final DiscoveryControllerInterface controller;

    public DiscoveryTabPanel() {
        setLayout(new BorderLayout());

        // Instantiate the controller
        controller = DiscoveryTabController.getInstance();
        controller.registerView(this); // Register 'this' panel as the view

        // --- Top Panel: dateFrom + discover button ---
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        topPanel.add(new JLabel("Datum från:"));
        String dateToday = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        dateFromField = new JTextField(dateToday, 10);
        topPanel.add(dateFromField);

        discoverButton = new JButton("Upptäck event");
        topPanel.add(discoverButton);

        add(topPanel, BorderLayout.NORTH);

        // ---------------------------------------------
        // Table with ID in hidden column
        // ---------------------------------------------
        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{},
                new String[]{"ID (hidden)", "Loppis", "Stad", "Öppnar", "Stänger"}
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        eventsTable = new JTable(model);
        // Hide column 0 from view
        eventsTable.removeColumn(eventsTable.getColumnModel().getColumn(0));
        add(new JScrollPane(eventsTable), BorderLayout.CENTER);

        // --- Bottom: cashier code + get token + status ---
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        bottomPanel.add(new JLabel("Kassakod:"));
        cashierCodeField = new JTextField("", 8);
        bottomPanel.add(cashierCodeField);

        getTokenButton = new JButton("Hämta Token");
        bottomPanel.add(getTokenButton);

        statusLabel = new JLabel(" ");
        bottomPanel.add(statusLabel);

        add(bottomPanel, BorderLayout.SOUTH);

        // Add listeners
        discoverButton.addActionListener(e -> {
            String dateFrom = dateFromField.getText().trim();
            controller.discoverEvents(dateFrom);
        });

        getTokenButton.addActionListener(e -> {
            int rowIndex = getSelectedTableRow();
            String eventId = getEventIdForRow(rowIndex);
            String code = getCashierCode();
            controller.fetchApiKey(eventId, code);
        });
    }

    // -- DiscoveryPanelInterface methods --

    @Override
    public void clearEventsTable() {
        ((DefaultTableModel) eventsTable.getModel()).setRowCount(0);
    }

    @Override
    public void populateEventsTable(List<Event> events) {
        DefaultTableModel model = (DefaultTableModel) eventsTable.getModel();
        for (Event ev : events) {
            model.addRow(new Object[]{
                    ev.getId(),
                    ev.getName(),
                    ev.getAddressCity(),
                    ev.getStartDate(),
                    ev.getEndDate()
            });
        }
    }

    @Override
    public void showStatusMessage(String message) {
        statusLabel.setText(message);
    }

    @Override
    public int getSelectedTableRow() {
        return eventsTable.getSelectedRow();
    }

    @Override
    public String getEventIdForRow(int rowIndex) {
        if (rowIndex < 0) return null;
        // Because we removed the first column from the *view*,
        // we still have it in the model at index 0
        DefaultTableModel model = (DefaultTableModel) eventsTable.getModel();
        return (String) model.getValueAt(rowIndex, 0);
    }

    @Override
    public String getCashierCode() {
        return cashierCodeField.getText().trim();
    }

    // -- SelectabableTab method --
    @Override
    public void selected() {
        // If you want to auto-discover every time, do it here, e.g.:
        // controller.discoverEvents(dateFromField.getText().trim());
    }
}
