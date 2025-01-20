package se.goencoder.loppiskassan.ui;

import se.goencoder.iloppis.model.Event;
import se.goencoder.loppiskassan.controller.DiscoveryControllerInterface;
import se.goencoder.loppiskassan.controller.DiscoveryTabController;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class DiscoveryTabPanel extends JPanel implements DiscoveryPanelInterface {

    private final DiscoveryControllerInterface controller;

    // -- Root layout has a CardLayout to toggle between normal "discovery" panel and "active event" panel --
    private final CardLayout rootCardLayout;
    private final JPanel rootCardPanel;

    // =========================================
    // =   Discovery Mode UI (the old stuff)   =
    // =========================================
    private JPanel discoveryModePanel;  // The panel with dateFrom, table, detail form, etc.
    private JTextField dateFromField;
    private JButton discoverButton;
    private JTable eventsTable;

    // "Detail" portion inside discovery mode
    private CardLayout detailCardLayout;
    private JPanel detailCardPanel;
    private JLabel noSelectionLabel;
    private JPanel detailFormPanel;

    // The fields in the detail form
    private JTextField eventNameField;
    private JTextArea eventDescriptionField;
    private JTextField eventAddressField;
    private JTextField marketOwnerSplitField;
    private JTextField vendorSplitField;
    private JTextField platformSplitField;
    private JLabel cashierCodeLabel;
    private JTextField cashierCodeField;

    private JButton getTokenButton; // "Öppna kassa" button in discovery mode

    // =========================================
    // =   Active Event Mode UI                =
    // =========================================
    private JPanel activeEventPanel;    // Simple summary of the chosen event
    private JLabel activeEventNameLabel;
    private JLabel activeEventDescLabel;
    private JLabel activeEventAddressLabel;
    private JButton changeEventButton;

    public DiscoveryTabPanel() {
        // Instantiate the controller
        controller = DiscoveryTabController.getInstance();
        controller.registerView(this);

        // The outer layout: CardLayout for root
        setLayout(new BorderLayout());
        rootCardLayout = new CardLayout();
        rootCardPanel = new JPanel(rootCardLayout);
        add(rootCardPanel, BorderLayout.CENTER);

        // Build both modes:
        discoveryModePanel = buildDiscoveryModePanel();
        activeEventPanel   = buildActiveEventPanel();

        // Add them to the rootCardPanel
        rootCardPanel.add(discoveryModePanel, "discoveryMode");
        rootCardPanel.add(activeEventPanel,   "activeEvent");

        // Show discovery mode by default
        rootCardLayout.show(rootCardPanel, "discoveryMode");
    }

    // -----------------------------
    // Building the "discoveryMode" UI (the older table approach)
    // -----------------------------
    private JPanel buildDiscoveryModePanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Top panel: "Datum från", "Hämta loppisar"
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Datum från:"));
        String dateToday = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        dateFromField = new JTextField(dateToday, 10);
        topPanel.add(dateFromField);

        discoverButton = new JButton("Hämta loppisar");
        topPanel.add(discoverButton);

        panel.add(topPanel, BorderLayout.NORTH);

        // Center: a split pane with top half = table, bottom half = detailCard
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.5);

        // Table
        DefaultTableModel model = new DefaultTableModel(
                new Object[][] {},
                new String[] {"ID (hidden)", "Loppis", "Stad", "Öppnar", "Stänger"}
        ) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        eventsTable = new JTable(model);

        // Hide the first column
        eventsTable.removeColumn(eventsTable.getColumnModel().getColumn(0));

        // Table selection listener
        eventsTable.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (!e.getValueIsAdjusting()) {
                int rowIndex = getSelectedTableRow();
                if (rowIndex >= 0) {
                    String eventId = getEventIdForRow(rowIndex);
                    controller.eventSelected(eventId);
                }
            }
        });

        JScrollPane tableScroll = new JScrollPane(eventsTable);
        splitPane.setTopComponent(tableScroll);

        // The bottom half: another CardLayout for detail/noSelection
        detailCardLayout = new CardLayout();
        detailCardPanel  = new JPanel(detailCardLayout);

        // 1) A label for "no selection"
        noSelectionLabel = new JLabel("Välj ett event i listan ovan …", SwingConstants.CENTER);
        JPanel cardLabel = new JPanel(new BorderLayout());
        cardLabel.add(noSelectionLabel, BorderLayout.CENTER);

        // 2) The detail form
        detailFormPanel = buildDetailFormPanel();

        detailCardPanel.add(cardLabel,      "noSelection");
        detailCardPanel.add(detailFormPanel,"detailForm");
        detailCardLayout.show(detailCardPanel, "noSelection");

        splitPane.setBottomComponent(detailCardPanel);

        panel.add(splitPane, BorderLayout.CENTER);

        // Bottom: "Öppna kassa" button
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        getTokenButton = new JButton("Öppna Kassa");
        bottomPanel.add(getTokenButton);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        // Add listeners
        discoverButton.addActionListener(e -> {
            String dateFrom = dateFromField.getText().trim();
            controller.discoverEvents(dateFrom);
        });
        getTokenButton.addActionListener(e -> {
            int rowIndex = getSelectedTableRow();
            String eventId = getEventIdForRow(rowIndex);
            String code    = getCashierCode();
            controller.openRegister(eventId, code);
        });

        return panel;
    }

    private JPanel buildDetailFormPanel() {
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

        return panel;
    }

    // -----------------------------
    // Building the "activeEvent" UI
    // -----------------------------
    private JPanel buildActiveEventPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        // Title label
        JLabel titleLabel = new JLabel("Aktivt Event - Kassa Öppen");
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(titleLabel);

        // Event name
        activeEventNameLabel = new JLabel("Eventnamn: ???");
        activeEventNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(activeEventNameLabel);

        // Event desc
        activeEventDescLabel = new JLabel("Beskrivning: ???");
        activeEventDescLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(activeEventDescLabel);

        // Event address
        activeEventAddressLabel = new JLabel("Adress: ???");
        activeEventAddressLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        panel.add(activeEventAddressLabel);

        // "Change event" button
        panel.add(Box.createVerticalStrut(10));
        changeEventButton = new JButton("Byt event");
        changeEventButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        changeEventButton.addActionListener(e -> controller.changeEventRequested());
        panel.add(changeEventButton);

        return panel;
    }

    // --------------------------------------------------------------------
    // Implementation of DiscoveryPanelInterface
    // (some existing methods remain the same as your original code)
    // --------------------------------------------------------------------

    @Override
    public void showDetailForm(boolean show) {
        if (show) {
            detailCardLayout.show(detailCardPanel, "detailForm");
        } else {
            detailCardLayout.show(detailCardPanel, "noSelection");
        }
    }

    @Override
    public void showCashierCode(boolean show) {
        cashierCodeLabel.setVisible(show);
        cashierCodeField.setVisible(show);
    }

    // Called once an event is selected
    @Override
    public void clearEventsTable() {
        DefaultTableModel model = (DefaultTableModel) eventsTable.getModel();
        model.setRowCount(0);
        // Also revert to "noSelection" in the detail area
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
        DefaultTableModel model = (DefaultTableModel) eventsTable.getModel();
        return (String) model.getValueAt(rowIndex, 0);
    }

    @Override
    public String getCashierCode() {
        return cashierCodeField.getText().trim();
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

    // The new methods for toggling between discovery vs. active event
    @Override
    public void setRegisterOpened(boolean opened) {
        if (opened) {
            rootCardLayout.show(rootCardPanel, "activeEvent");
        } else {
            rootCardLayout.show(rootCardPanel, "discoveryMode");
        }
    }

    @Override
    public void showActiveEventInfo(String eventName, String description, String address) {
        activeEventNameLabel.setText("Eventnamn: " + (eventName == null ? "" : eventName));
        activeEventDescLabel.setText("Beskrivning: " + (description == null ? "" : description));
        activeEventAddressLabel.setText("Adress: " + (address == null ? "" : address));
    }

    @Override
    public void setChangeEventButtonVisible(boolean visible) {
        changeEventButton.setVisible(visible);
    }

    @Override
    public void selected() {
        controller.initUIState();
    }

    // Utility
    private int parseOrDefault(String text, int defaultValue) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}
