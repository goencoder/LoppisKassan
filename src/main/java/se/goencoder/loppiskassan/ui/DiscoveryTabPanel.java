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

    // CardLayout to switch between "discovery mode" and "active event" views.
    private final CardLayout rootCardLayout;
    private final JPanel rootCardPanel;

    // Components for discovery mode
    private JTextField dateFromField;
    private JTable eventsTable;
    private CardLayout detailCardLayout;
    private JPanel detailCardPanel;
    private JTextField eventNameField;
    private JTextArea eventDescriptionField;
    private JTextField eventAddressField;
    private JTextField marketOwnerSplitField;
    private JTextField vendorSplitField;
    private JTextField platformSplitField;
    private JLabel cashierCodeLabel;
    private JTextField cashierCodeField;
    private JButton getTokenButton;

    // Components for active event mode
    private JLabel activeEventNameLabel;
    private JLabel activeEventDescLabel;
    private JLabel activeEventAddressLabel;
    private JButton changeEventButton;
    private JLabel marketOwnerSplitLabel;
    private JLabel vendorSplitLabel;
    private JLabel platformSplitLabel;

    public DiscoveryTabPanel() {
        controller = DiscoveryTabController.getInstance();
        controller.registerView(this);

        // Set up the main layout and root card panel
        setLayout(new BorderLayout());
        rootCardLayout = new CardLayout();
        rootCardPanel = new JPanel(rootCardLayout);
        add(rootCardPanel, BorderLayout.CENTER);

        // Initialize the two main panels
        JPanel discoveryModePanel = buildDiscoveryModePanel();
        JPanel activeEventPanel = buildActiveEventPanel();

        rootCardPanel.add(discoveryModePanel, "discoveryMode");
        rootCardPanel.add(activeEventPanel, "activeEvent");

        // Load initial state based on stored configuration
        initializeState();
    }

    /**
     * Initializes the UI state by checking for a saved event ID in the configuration.
     * If no event ID is saved, defaults to discovery mode.
     */
    private void initializeState() {
        String savedEventId = ConfigurationStore.EVENT_ID_STR.get();
        if (savedEventId == null || savedEventId.isEmpty()) {
            rootCardLayout.show(rootCardPanel, "discoveryMode");
        }
        controller.initUIState();
    }

    /**
     * Builds the panel for "discovery mode," where users can browse and select events.
     */
    private JPanel buildDiscoveryModePanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Top panel with "Datum från" field and discovery button
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(new JLabel("Datum från:"));
        dateFromField = new JTextField(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), 10);
        topPanel.add(dateFromField);

        JButton discoverButton = new JButton("Hämta loppisar");
        topPanel.add(discoverButton);
        panel.add(topPanel, BorderLayout.NORTH);

        // Split pane with event table on top and detail card below
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.5);

        eventsTable = createEventsTable();
        splitPane.setTopComponent(new JScrollPane(eventsTable));

        detailCardPanel = createDetailCardPanel();
        splitPane.setBottomComponent(detailCardPanel);

        panel.add(splitPane, BorderLayout.CENTER);

        // Bottom panel with "Öppna Kassa" button
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        getTokenButton = new JButton("Öppna Kassa");
        bottomPanel.add(getTokenButton);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        // Event listeners for buttons
        discoverButton.addActionListener(e -> controller.discoverEvents(dateFromField.getText().trim()));
        getTokenButton.addActionListener(e -> {
            int rowIndex = getSelectedTableRow();
            controller.openRegister(getEventIdForRow(rowIndex), getCashierCode());
        });

        return panel;
    }

    /**
     * Creates the JTable for displaying events and sets up its selection listener.
     */
    private JTable createEventsTable() {
        DefaultTableModel model = new DefaultTableModel(new Object[][]{}, new String[]{"ID (hidden)", "Loppis", "Stad", "Öppnar", "Stänger"}) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.removeColumn(table.getColumnModel().getColumn(0)); // Hide the ID column

        // Selection listener to update the detail view when an event is selected
        table.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (!e.getValueIsAdjusting()) {
                int rowIndex = getSelectedTableRow();
                if (rowIndex >= 0) {
                    controller.eventSelected(getEventIdForRow(rowIndex));
                }
            }
        });

        return table;
    }

    /**
     * Creates the detail card panel with two cards: "no selection" and the event detail form.
     */
    private JPanel createDetailCardPanel() {
        detailCardLayout = new CardLayout();
        JPanel panel = new JPanel(detailCardLayout);

        JLabel noSelectionLabel = new JLabel("Välj en loppis i listan …", SwingConstants.CENTER);
        panel.add(noSelectionLabel, "noSelection");

        JPanel detailFormPanel = buildDiscoveryDetailForm();
        panel.add(detailFormPanel, "detailForm");

        return panel;
    }

    /**
     * Builds the form for displaying and editing event details in discovery mode.
     */
    private JPanel buildDiscoveryDetailForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5,5);

        // Event details (left)
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.5;
        panel.add(buildEventDetailsPanel(), gbc);

        // Revenue split details (right)
        gbc.gridx = 1;
        panel.add(buildRevenueSplitPanel(), gbc);

        // Cashier code input (bottom)
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        panel.add(buildCashierCodePanel(), gbc);

        return panel;
    }

    private JPanel buildEventDetailsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Loppisdetaljer"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Name field
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Name:"), gbc);
        gbc.gridx = 1;
        eventNameField = new JTextField(20);
        panel.add(eventNameField, gbc);

        // Description field
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Description:"), gbc);
        gbc.gridx = 1;
        eventDescriptionField = new JTextArea(3, 20);
        panel.add(new JScrollPane(eventDescriptionField), gbc);

        // Address field
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("Address:"), gbc);
        gbc.gridx = 1;
        eventAddressField = new JTextField(20);
        panel.add(eventAddressField, gbc);

        return panel;
    }

    private JPanel buildRevenueSplitPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Försäljningsdelning"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Market Owner Split
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Market Owner (%):"), gbc);
        gbc.gridx = 1;
        marketOwnerSplitField = new JTextField(5);
        panel.add(marketOwnerSplitField, gbc);

        // Vendor Split
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Vendor (%):"), gbc);
        gbc.gridx = 1;
        vendorSplitField = new JTextField(5);
        panel.add(vendorSplitField, gbc);

        // Platform Split
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("Platform (%):"), gbc);
        gbc.gridx = 1;
        platformSplitField = new JTextField(5);
        panel.add(platformSplitField, gbc);

        return panel;
    }

    private JPanel buildCashierCodePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        cashierCodeLabel = new JLabel("Cashier Code:");
        cashierCodeField = new JTextField(8);
        panel.add(cashierCodeLabel);
        panel.add(cashierCodeField);
        return panel;
    }

    /**
     * Builds the panel for "active event" mode, which displays read-only event details.
     */
    private JPanel buildActiveEventPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Vald Loppis"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Event Details Section
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(createEventDetailsPanel(), gbc);

        // Revenue Split Section
        gbc.gridy = 1;
        panel.add(createRevenueSplitPanel(), gbc);

        // Change Event Button
        gbc.gridy = 2;
        panel.add(createChangeEventButton(), gbc);

        return panel;
    }

    private JPanel createEventDetailsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Loppisdetaljer"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Event Name
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Namn:"), gbc);
        gbc.gridx = 1;
        activeEventNameLabel = new JLabel("???");
        panel.add(activeEventNameLabel, gbc);

        // Event Description
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Beskrivning:"), gbc);
        gbc.gridx = 1;
        activeEventDescLabel = new JLabel("???");
        panel.add(activeEventDescLabel, gbc);

        // Event Address
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("Address:"), gbc);
        gbc.gridx = 1;
        activeEventAddressLabel = new JLabel("???");
        panel.add(activeEventAddressLabel, gbc);

        return panel;
    }

    private JPanel createRevenueSplitPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createTitledBorder("Försäljningsdelning"));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);

        // Market Owner Split
        gbc.gridx = 0;
        gbc.gridy = 0;
        panel.add(new JLabel("Arrangör (%):"), gbc);
        gbc.gridx = 1;
        marketOwnerSplitLabel = new JLabel("???");
        panel.add(marketOwnerSplitLabel, gbc);

        // Vendor Split
        gbc.gridx = 0;
        gbc.gridy = 1;
        panel.add(new JLabel("Säljare (%):"), gbc);
        gbc.gridx = 1;
        vendorSplitLabel = new JLabel("???");
        panel.add(vendorSplitLabel, gbc);

        // Platform Split
        gbc.gridx = 0;
        gbc.gridy = 2;
        panel.add(new JLabel("iLoppis (%):"), gbc);
        gbc.gridx = 1;
        platformSplitLabel = new JLabel("???");
        panel.add(platformSplitLabel, gbc);

        return panel;
    }

    private JPanel createChangeEventButton() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 100));
        changeEventButton = new JButton("Byt Loppis");
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
                    ev.getStartTime(),
                    ev.getEndTime()
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

