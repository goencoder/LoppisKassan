package se.goencoder.loppiskassan.ui;

import se.goencoder.iloppis.model.Event;
import se.goencoder.iloppis.model.RevenueSplit;
import se.goencoder.loppiskassan.config.ConfigurationStore;
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
    // =   Discovery Mode references
    // =========================================
    private JTextField dateFromField;


    // "Detail" portion inside discovery mode
    private CardLayout detailCardLayout;
    private JPanel detailCardPanel;
    private JTable eventsTable;

    private JTextField eventNameField;     // Single reference
    private JTextArea  eventDescriptionField;
    private JTextField eventAddressField;
    private JTextField marketOwnerSplitField;
    private JTextField vendorSplitField;
    private JTextField platformSplitField;
    private JLabel     cashierCodeLabel;
    private JTextField cashierCodeField;

    private JButton getTokenButton;

    // =========================================
    // =   Active Event references
    // =========================================
    private JLabel activeEventNameLabel;
    private JLabel activeEventDescLabel;
    private JLabel activeEventAddressLabel;
    private JButton changeEventButton;
    private JLabel marketOwnerSplitLabel;
    private JLabel vendorSplitLabel;
    private JLabel platformSplitLabel;



    public DiscoveryTabPanel() {
        // 1) controller
        controller = DiscoveryTabController.getInstance();
        controller.registerView(this);

        // 2) root
        setLayout(new BorderLayout());
        rootCardLayout = new CardLayout();
        rootCardPanel = new JPanel(rootCardLayout);
        add(rootCardPanel, BorderLayout.CENTER);

        // 3) build the two main cards
        JPanel discoveryModePanel = buildDiscoveryModePanel();
        JPanel activeEventPanel   = buildActiveEventPanel();

        rootCardPanel.add(discoveryModePanel, "discoveryMode");
        rootCardPanel.add(activeEventPanel,   "activeEvent");

        // 4) Check for a stored event ID and simulate selection
        String savedEventId = ConfigurationStore.EVENT_ID_STR.get();
        if (savedEventId != null && !savedEventId.isEmpty()) {
            // Fetch events first
            controller.discoverEvents(LocalDate.now().toString()); // Replace with desired default date
            // Select the saved event
            controller.eventSelected(savedEventId);
        } else {
            // Default to discovery mode
            rootCardLayout.show(rootCardPanel, "discoveryMode");
        }
        controller.initUIState();
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

        JButton discoverButton = new JButton("Hämta loppisar");
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
        tableScroll.setPreferredSize(new Dimension(tableScroll.getPreferredSize().width, eventsTable.getRowHeight() * 5));
        splitPane.setTopComponent(tableScroll);
        // Card #1: "no selection" label
        JLabel noSelectionLabel = new JLabel("Välj ett event …", SwingConstants.CENTER);
        JPanel noSelectionPanel = new JPanel(new BorderLayout());
        noSelectionPanel.add(noSelectionLabel, BorderLayout.CENTER);

        // Card #2: the detail form (read-write)
        // The actual panel with eventNameField, etc.
        JPanel detailFormPanel = buildDiscoveryDetailForm();
        detailCardLayout = new CardLayout();
        detailCardPanel  = new JPanel(detailCardLayout);
        detailCardPanel.add(noSelectionPanel,   "noSelection");
        detailCardPanel.add(detailFormPanel,    "detailForm");
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

    /**
     * Build the "detail form" for the discovery mode (read/write).
     */
    private JPanel buildDiscoveryDetailForm() {
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill   = GridBagConstraints.BOTH;
        gbc.insets = new Insets(5,5,5,5);

        // Left: Event Details
        JPanel eventDetailsPanel = new JPanel(new GridBagLayout());
        eventDetailsPanel.setBorder(BorderFactory.createTitledBorder("Event Details"));
        GridBagConstraints edGbc = new GridBagConstraints();
        edGbc.fill   = GridBagConstraints.HORIZONTAL;
        edGbc.insets = new Insets(5,5,5,5);

        edGbc.gridx = 0; edGbc.gridy = 0;
        eventDetailsPanel.add(new JLabel("Name:"), edGbc);
        edGbc.gridx = 1;
        eventNameField = new JTextField("", 20);
        eventNameField.setEditable(true);
        eventDetailsPanel.add(eventNameField, edGbc);

        edGbc.gridx = 0; edGbc.gridy++;
        eventDetailsPanel.add(new JLabel("Description:"), edGbc);
        edGbc.gridx = 1;
        eventDescriptionField = new JTextArea(3, 20);
        eventDescriptionField.setLineWrap(true);
        eventDescriptionField.setWrapStyleWord(true);
        JScrollPane descScroll = new JScrollPane(eventDescriptionField);
        eventDetailsPanel.add(descScroll, edGbc);

        edGbc.gridx = 0; edGbc.gridy++;
        eventDetailsPanel.add(new JLabel("Address:"), edGbc);
        edGbc.gridx = 1;
        eventAddressField = new JTextField("", 20);
        eventAddressField.setEditable(true);
        eventDetailsPanel.add(eventAddressField, edGbc);

        // Right: Revenue split
        JPanel revenueSplitPanel = new JPanel(new GridBagLayout());
        revenueSplitPanel.setBorder(BorderFactory.createTitledBorder("Revenue Split"));
        GridBagConstraints rsGbc = new GridBagConstraints();
        rsGbc.fill   = GridBagConstraints.HORIZONTAL;
        rsGbc.insets = new Insets(5,5,5,5);

        rsGbc.gridx = 0; rsGbc.gridy = 0;
        revenueSplitPanel.add(new JLabel("Market Owner (%):"), rsGbc);
        rsGbc.gridx = 1;
        marketOwnerSplitField = new JTextField("10", 5);
        revenueSplitPanel.add(marketOwnerSplitField, rsGbc);

        rsGbc.gridx = 0; rsGbc.gridy++;
        revenueSplitPanel.add(new JLabel("Vendor (%):"), rsGbc);
        rsGbc.gridx = 1;
        vendorSplitField = new JTextField("85", 5);
        revenueSplitPanel.add(vendorSplitField, rsGbc);

        rsGbc.gridx = 0; rsGbc.gridy++;
        revenueSplitPanel.add(new JLabel("Platform (%):"), rsGbc);
        rsGbc.gridx = 1;
        platformSplitField = new JTextField("5", 5);
        revenueSplitPanel.add(platformSplitField, rsGbc);

        // Place them side by side
        gbc.gridx = 0; gbc.gridy = 0;
        gbc.weightx = 0.5;
        gbc.weighty = 1.0;
        mainPanel.add(eventDetailsPanel, gbc);

        gbc.gridx = 1; // next column
        gbc.gridy = 0;
        gbc.weightx = 0.5;
        gbc.weighty = 1.0;
        mainPanel.add(revenueSplitPanel, gbc);

        // Cashier code row
        gbc.gridx = 0; gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        gbc.weighty = 0.0;
        JPanel cashierPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        cashierCodeLabel = new JLabel("Cashier Code:");
        cashierCodeField = new JTextField("", 8);
        cashierPanel.add(cashierCodeLabel);
        cashierPanel.add(cashierCodeField);

        mainPanel.add(cashierPanel, gbc);

        return mainPanel;
    }

    // -- Active event panel (READ-ONLY, simpler labels) --
    private JPanel buildActiveEventPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Active Event"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Event Details Section
        JPanel eventDetailsPanel = new JPanel(new GridBagLayout());
        eventDetailsPanel.setBorder(BorderFactory.createTitledBorder("Event Details"));
        GridBagConstraints edGbc = new GridBagConstraints();
        edGbc.fill = GridBagConstraints.HORIZONTAL;
        edGbc.insets = new Insets(5, 5, 5, 5);

        edGbc.gridx = 0; edGbc.gridy = 0;
        eventDetailsPanel.add(new JLabel("Name:"), edGbc);
        edGbc.gridx = 1;
        activeEventNameLabel = new JLabel("???");
        eventDetailsPanel.add(activeEventNameLabel, edGbc);

        edGbc.gridx = 0; edGbc.gridy++;
        eventDetailsPanel.add(new JLabel("Description:"), edGbc);
        edGbc.gridx = 1;
        activeEventDescLabel = new JLabel("???");
        eventDetailsPanel.add(activeEventDescLabel, edGbc);

        edGbc.gridx = 0; edGbc.gridy++;
        eventDetailsPanel.add(new JLabel("Address:"), edGbc);
        edGbc.gridx = 1;
        activeEventAddressLabel = new JLabel("???");
        eventDetailsPanel.add(activeEventAddressLabel, edGbc);

        // Revenue Split Section
        JPanel revenueSplitPanel = new JPanel(new GridBagLayout());
        revenueSplitPanel.setBorder(BorderFactory.createTitledBorder("Revenue Split"));
        GridBagConstraints rsGbc = new GridBagConstraints();
        rsGbc.fill = GridBagConstraints.HORIZONTAL;
        rsGbc.insets = new Insets(5, 5, 5, 5);

        rsGbc.gridx = 0; rsGbc.gridy = 0;
        revenueSplitPanel.add(new JLabel("Market Owner (%):"), rsGbc);
        rsGbc.gridx = 1;
        marketOwnerSplitLabel = new JLabel("???");
        revenueSplitPanel.add(marketOwnerSplitLabel, rsGbc);

        rsGbc.gridx = 0; rsGbc.gridy++;
        revenueSplitPanel.add(new JLabel("Vendor (%):"), rsGbc);
        rsGbc.gridx = 1;
        vendorSplitLabel = new JLabel("???");
        revenueSplitPanel.add(vendorSplitLabel, rsGbc);

        rsGbc.gridx = 0; rsGbc.gridy++;
        revenueSplitPanel.add(new JLabel("Platform (%):"), rsGbc);
        rsGbc.gridx = 1;
        platformSplitLabel = new JLabel("???");
        revenueSplitPanel.add(platformSplitLabel, rsGbc);

        // Add Sections to Main Panel
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0;
        panel.add(eventDetailsPanel, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 1.0;
        panel.add(revenueSplitPanel, gbc);

        // Change Event Button
        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.PAGE_END;
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 100));
        changeEventButton = new JButton("Change Event");
        changeEventButton.addActionListener(e -> controller.changeEventRequested());
        buttonPanel.add(changeEventButton);
        panel.add(buttonPanel, gbc);

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


    private void showCashierCode(boolean show) {
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


    private int getSelectedTableRow() {
        return eventsTable.getSelectedRow();
    }


    private String getEventIdForRow(int rowIndex) {
        if (rowIndex < 0) return null;
        DefaultTableModel model = (DefaultTableModel) eventsTable.getModel();
        return (String) model.getValueAt(rowIndex, 0);
    }


    private String getCashierCode() {
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
    public void showActiveEventInfo(Event event, RevenueSplit split) {
        // Update Event Details
        activeEventNameLabel.setText(event.getName());
        activeEventDescLabel.setText(event.getDescription());
        activeEventAddressLabel.setText(event.getAddressStreet() + ", " + event.getAddressCity());
        marketOwnerSplitLabel.setText(String.valueOf(split.getMarketOwnerPercentage()));
        vendorSplitLabel.setText(String.valueOf(split.getVendorPercentage()));
        platformSplitLabel.setText(String.valueOf(split.getPlatformProviderPercentage()));
        // Switch to Active Event Panel
        rootCardLayout.show(rootCardPanel, "activeEvent");
    }

    @Override
    public void setChangeEventButtonVisible(boolean visible) {
        changeEventButton.setVisible(visible);
    }


    @Override
    public void selected() {
        controller.initUIState();
    }


}

