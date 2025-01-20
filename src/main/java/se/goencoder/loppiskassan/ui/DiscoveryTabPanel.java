package se.goencoder.loppiskassan.ui;

import se.goencoder.iloppis.model.Event;
import se.goencoder.loppiskassan.controller.DiscoveryControllerInterface;
import se.goencoder.loppiskassan.controller.DiscoveryTabController;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DiscoveryTabPanel extends JPanel implements DiscoveryPanelInterface {

    private final JTextField dateFromField;
    private final JButton discoverButton;
    private final JTable eventsTable;

    // For the detail area, we use a CardLayout:
    private final JPanel detailCardPanel;
    private final CardLayout detailCardLayout;

    // "No selection" card
    private final JLabel noSelectionLabel;

    // "Detail form" card
    private final JPanel detailFormPanel;
    private  JTextField eventNameField;
    private  JTextArea eventDescriptionField;
    private  JTextField eventAddressField;
    private  JTextField marketOwnerSplitField;
    private  JTextField vendorSplitField;
    private  JTextField platformSplitField;
    private  JLabel cashierCodeLabel;       // We want to hide this if offline
    private  JTextField cashierCodeField;   // We want to hide this if offline

    private final JButton getTokenButton;

    private final DiscoveryControllerInterface controller;

    public DiscoveryTabPanel() {
        setLayout(new BorderLayout());

        // Instantiate the controller
        controller = DiscoveryTabController.getInstance();
        controller.registerView(this);

        // ----------------
        // TOP: dateFrom + discover
        // ----------------
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Datum från:"));
        String dateToday = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        dateFromField = new JTextField(dateToday, 10);
        topPanel.add(dateFromField);

        discoverButton = new JButton("Hämta loppisar");
        topPanel.add(discoverButton);

        add(topPanel, BorderLayout.NORTH);

        // ----------------
        // CENTER SPLIT: top half = events table, bottom half = "detailCardPanel" with CardLayout
        // ----------------
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.5); // half/half

        // ============ TOP half: events table
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

        // Add selection listener
        eventsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int rowIndex = getSelectedTableRow();
                    if (rowIndex >= 0) {
                        String eventId = getEventIdForRow(rowIndex);
                        controller.eventSelected(eventId);
                    }
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(eventsTable);
        splitPane.setTopComponent(tableScroll);

        // ============ BOTTOM half: detailCardPanel with CardLayout
        detailCardLayout = new CardLayout();
        detailCardPanel = new JPanel(detailCardLayout);

        // 1) "noSelection" card: just a label
        noSelectionLabel = new JLabel("Välj ett event i listan ovan …", SwingConstants.CENTER);
        JPanel cardLabel = new JPanel(new BorderLayout());
        cardLabel.add(noSelectionLabel, BorderLayout.CENTER);

        // 2) "detailForm" card
        detailFormPanel = createDetailFormPanel();

        detailCardPanel.add(cardLabel, "noSelection");
        detailCardPanel.add(detailFormPanel, "detailForm");

        // Start with "noSelection" visible
        detailCardLayout.show(detailCardPanel, "noSelection");

        splitPane.setBottomComponent(detailCardPanel);
        add(splitPane, BorderLayout.CENTER);

        // ----------------
        // BOTTOM: "Öppna kassa" (getTokenButton)
        // ----------------
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        getTokenButton = new JButton("Öppna Kassa");
        bottomPanel.add(getTokenButton);
        add(bottomPanel, BorderLayout.SOUTH);

        // ----------------
        // Add Listeners
        // ----------------
        discoverButton.addActionListener(e -> {
            String dateFrom = dateFromField.getText().trim();
            controller.discoverEvents(dateFrom);
        });

        getTokenButton.addActionListener(e -> {
            int rowIndex = getSelectedTableRow();
            String eventId = getEventIdForRow(rowIndex);
            String code = getCashierCode();
            controller.openRegister(eventId, code);
        });
    }

    /** Builds the "detailFormPanel" card with GridBagLayout. */
    private JPanel createDetailFormPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(3, 3, 3, 3);
        gbc.gridx = 0;
        gbc.gridy = 0;

        // Name
        panel.add(new JLabel("Namn:"), gbc);
        gbc.gridx++;
        eventNameField = new JTextField("", 20);
        eventNameField.setEditable(false);
        panel.add(eventNameField, gbc);

        // Desc
        gbc.gridx = 0; gbc.gridy++;
        panel.add(new JLabel("Beskrivning:"), gbc);
        gbc.gridx++;
        eventDescriptionField = new JTextArea(3, 20);
        eventDescriptionField.setEditable(false);
        eventDescriptionField.setLineWrap(true);
        eventDescriptionField.setWrapStyleWord(true);
        eventDescriptionField.setBorder(eventNameField.getBorder());
        eventDescriptionField.setBackground(eventNameField.getBackground());
        JScrollPane descriptionScrollPane = new JScrollPane(eventDescriptionField);
        descriptionScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        descriptionScrollPane.setBorder(BorderFactory.createEmptyBorder());
        panel.add(descriptionScrollPane, gbc);

        // Address
        gbc.gridx = 0; gbc.gridy++;
        panel.add(new JLabel("Adress:"), gbc);
        gbc.gridx++;
        eventAddressField = new JTextField("", 20);
        eventAddressField.setEditable(false);
        panel.add(eventAddressField, gbc);

        // Splits: Loppisägare
        gbc.gridx = 0; gbc.gridy++;
        panel.add(new JLabel("Loppisägare (%):"), gbc);
        gbc.gridx++;
        marketOwnerSplitField = new JTextField("10", 5);
        marketOwnerSplitField.setEditable(false);
        panel.add(marketOwnerSplitField, gbc);

        // Splits: Säljare
        gbc.gridx = 0; gbc.gridy++;
        panel.add(new JLabel("Säljare (%):"), gbc);
        gbc.gridx++;
        vendorSplitField = new JTextField("85", 5);
        vendorSplitField.setEditable(false);
        panel.add(vendorSplitField, gbc);

        // Splits: iLoppis
        gbc.gridx = 0; gbc.gridy++;
        panel.add(new JLabel("iLoppis (%):"), gbc);
        gbc.gridx++;
        platformSplitField = new JTextField("5", 5);
        platformSplitField.setEditable(false);
        panel.add(platformSplitField, gbc);

        // Kassakod
        gbc.gridx = 0; gbc.gridy++;
        cashierCodeLabel = new JLabel("Kassakod:");
        panel.add(cashierCodeLabel, gbc);

        gbc.gridx++;
        cashierCodeField = new JTextField("", 8);
        panel.add(cashierCodeField, gbc);

        // Add action listeners for these splits if needed
        marketOwnerSplitField.addActionListener(e -> updateOfflineSplitsIfOffline());
        vendorSplitField.addActionListener(e -> updateOfflineSplitsIfOffline());
        platformSplitField.addActionListener(e -> updateOfflineSplitsIfOffline());

        return panel;
    }

    private void updateOfflineSplitsIfOffline() {
        // Only call controller if the splits are editable => offline
        if (marketOwnerSplitField.isEditable()) {
            int mo = parseOrDefault(marketOwnerSplitField.getText(), 0);
            int ve = parseOrDefault(vendorSplitField.getText(), 0);
            int pl = parseOrDefault(platformSplitField.getText(), 0);
            controller.setRevenueSplitFromUI(mo, ve, pl);
        }
    }

    private int parseOrDefault(String text, int defaultValue) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }

    // ----- DiscoveryPanelInterface methods -----

    @Override
    public void clearEventsTable() {
        ((DefaultTableModel) eventsTable.getModel()).setRowCount(0);
        // Also revert to "noSelection" card whenever we clear
        detailCardLayout.show(detailCardPanel, "noSelection");
    }

    @Override
    public void populateEventsTable(List<Event> events) {
        DefaultTableModel model = (DefaultTableModel) eventsTable.getModel();
        model.setRowCount(0); // Clear first
        for (Event ev : events) {
            model.addRow(new Object[]{
                    ev.getId(),
                    ev.getName(),
                    (ev.getAddressCity() == null ? "" : ev.getAddressCity()),
                    ev.getStartDate(),
                    ev.getEndDate()
            });
        }
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

    // Show or hide the detail form card
    @Override
    public void showDetailForm(boolean show) {
        if (show) {
            detailCardLayout.show(detailCardPanel, "detailForm");
        } else {
            detailCardLayout.show(detailCardPanel, "noSelection");
        }
    }

    // Hide the cashier code label + field if offline
    @Override
    public void showCashierCode(boolean show) {
        cashierCodeLabel.setVisible(show);
        cashierCodeField.setVisible(show);
    }

    @Override
    public void setEventName(String name) {
        eventNameField.setText(name);
    }

    @Override
    public void setEventDescription(String description) {
        eventDescriptionField.setText(description != null ? description : "");
    }

    @Override
    public void setEventAddress(String address) {
        eventAddressField.setText(address);
    }

    @Override
    public void setOfflineMode(boolean offline) {
        // In a simpler UI, we could do something like setting a label: "OFFLINE MODE"
        // but here let's do nothing except maybe show/hide cashier code:
        showCashierCode(!offline);
    }

    @Override
    public void setRevenueSplitEditable(boolean editable) {
        marketOwnerSplitField.setEditable(editable);
        vendorSplitField.setEditable(editable);
        platformSplitField.setEditable(editable);
    }

    @Override
    public void setRevenueSplit(float marketOwner, float vendor, float platform) {
        marketOwnerSplitField.setText(String.valueOf(marketOwner));
        vendorSplitField.setText(String.valueOf(vendor));
        platformSplitField.setText(String.valueOf(platform));
    }

    @Override
    public int getMarketOwnerSplit() {
        return parseOrDefault(marketOwnerSplitField.getText(), 0);
    }

    @Override
    public int getVendorSplit() {
        return parseOrDefault(vendorSplitField.getText(), 0);
    }

    @Override
    public int getPlatformSplit() {
        return parseOrDefault(platformSplitField.getText(), 0);
    }

    @Override
    public void setCashierButtonEnabled(boolean enabled) {
        getTokenButton.setEnabled(enabled);
    }

    @Override
    public void clearCashierCodeField() {
        cashierCodeField.setText("******");
    }

    @Override
    public void selected() {
        controller.initUIState();
    }
}
