package se.goencoder.loppiskassan.ui;

import se.goencoder.iloppis.model.V1Event;
import se.goencoder.iloppis.model.V1RevenueSplit;
import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.controller.DiscoveryControllerInterface;
import se.goencoder.loppiskassan.controller.DiscoveryTabController;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.localization.LocalizationAware;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Discovery tab for <b>iLoppis</b> (online) mode.
 * Shows only online events fetched from the iLoppis backend.
 * No local events, no export/import — those live in {@link LocalDiscoveryTabPanel}.
 */
public class DiscoveryTabPanel extends JPanel implements DiscoveryPanelInterface, LocalizationAware {

    private final DiscoveryControllerInterface controller;

    // CardLayout to switch between "discovery mode" and "active event" views.
    private final CardLayout rootCardLayout;
    private final JPanel rootCardPanel;

    // Components for discovery mode
    private JLabel dateFromLabel;
    private JTextField dateFromField;
    private JButton discoverButton;

    // Online events table
    private JTable onlineEventsTable;
    private DefaultTableModel onlineEventsTableModel;

    private CardLayout detailCardLayout;
    private JPanel detailCardPanel;
    private JLabel noSelectionLabel;
    private JTextField eventNameField;
    private JTextArea eventDescriptionField;
    private JTextField eventAddressField;
    private JTextField marketOwnerSplitField;
    private JTextField vendorSplitField;
    private JTextField platformSplitField;
    private JLabel cashierCodeLabel;
    private JTextField cashierCodeField;
    private JButton getTokenButton;

    private TitledBorder discoveryDetailsBorder;
    private TitledBorder discoveryRevenueSplitBorder;
    private JLabel discoveryEventNameStaticLabel;
    private JLabel discoveryEventDescStaticLabel;
    private JLabel discoveryEventAddressStaticLabel;
    private JLabel discoveryMarketOwnerStaticLabel;
    private JLabel discoveryVendorStaticLabel;
    private JLabel discoveryPlatformStaticLabel;

    // Components for active event mode
    private JLabel activeEventNameLabel;
    private JLabel activeEventDescLabel;
    private JLabel activeEventAddressLabel;
    private JButton changeEventButton;
    private JLabel marketOwnerSplitLabel;
    private JLabel vendorSplitLabel;
    private JLabel platformSplitLabel;

    private TitledBorder selectedEventBorder;
    private TitledBorder detailsBorder;
    private TitledBorder revenueSplitBorder;
    private JLabel eventNameStaticLabel;
    private JLabel eventDescStaticLabel;
    private JLabel eventAddressStaticLabel;
    private JLabel marketOwnerStaticLabel;
    private JLabel vendorStaticLabel;
    private JLabel platformStaticLabel;

    public DiscoveryTabPanel() {
        controller = DiscoveryTabController.getInstance();
        controller.registerView(this);

        setLayout(new BorderLayout());
        rootCardLayout = new CardLayout();
        rootCardPanel = new JPanel(rootCardLayout);
        add(rootCardPanel, BorderLayout.CENTER);

        JPanel discoveryModePanel = buildDiscoveryModePanel();
        JPanel activeEventPanel = buildActiveEventPanel();

        rootCardPanel.add(discoveryModePanel, "discoveryMode");
        rootCardPanel.add(activeEventPanel, "activeEvent");

        initializeState();
        reloadTexts();
    }

    private void initializeState() {
        String savedEventId = ConfigurationStore.EVENT_ID_STR.get();
        if (savedEventId == null || savedEventId.isEmpty()) {
            rootCardLayout.show(rootCardPanel, "discoveryMode");
        }
        controller.initUIState();
    }

    // ── Discovery mode panel ──

    private JPanel buildDiscoveryModePanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Header: date picker + fetch button
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        dateFromLabel = new JLabel();
        headerPanel.add(dateFromLabel);
        dateFromField = new JTextField(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")), 10);
        headerPanel.add(dateFromField);
        discoverButton = new JButton();
        headerPanel.add(discoverButton);
        panel.add(headerPanel, BorderLayout.NORTH);

        // Online events table
        onlineEventsTable = createOnlineEventsTable();
        JScrollPane scrollPane = new JScrollPane(onlineEventsTable);
        scrollPane.setPreferredSize(new Dimension(600, 200));

        // Detail card below table
        detailCardPanel = createDetailCardPanel();
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollPane, detailCardPanel);
        splitPane.setResizeWeight(0.45);
        panel.add(splitPane, BorderLayout.CENTER);

        // Bottom: Open Register button
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        getTokenButton = new JButton();
        bottomPanel.add(getTokenButton);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        // Listeners
        discoverButton.addActionListener(e -> controller.discoverEvents(dateFromField.getText().trim()));
        getTokenButton.addActionListener(e -> {
            int rowIndex = onlineEventsTable.getSelectedRow();
            String eventId = rowIndex >= 0
                    ? (String) onlineEventsTableModel.getValueAt(rowIndex, 0) : null;
            String cashierCode = getCashierCode();
            if (eventId == null || eventId.isEmpty()) {
                controller.openRegister(eventId, cashierCode);
                return;
            }
            if (cashierCode.isEmpty()) {
                Popup.ERROR.showAndWait(
                        LocalizationManager.tr("error.title"),
                        LocalizationManager.tr("error.cashier_code_required"));
                return;
            }
            controller.openRegister(eventId, cashierCode);
        });

        return panel;
    }

    private JTable createOnlineEventsTable() {
        String[] columnNames = {
            LocalizationManager.tr("discovery.table.id_hidden"),
            LocalizationManager.tr("discovery.table.event"),
            LocalizationManager.tr("discovery.table.city"),
            LocalizationManager.tr("discovery.table.opens"),
            LocalizationManager.tr("discovery.table.closes")
        };

        onlineEventsTableModel = new DefaultTableModel(new Object[][]{}, columnNames) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(onlineEventsTableModel);
        table.removeColumn(table.getColumnModel().getColumn(0)); // Hide ID column

        table.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (!e.getValueIsAdjusting()) {
                int rowIndex = table.getSelectedRow();
                if (rowIndex >= 0) {
                    String eventId = (String) onlineEventsTableModel.getValueAt(rowIndex, 0);
                    controller.eventSelected(eventId);
                }
            }
        });

        return table;
    }

    // ── Detail card ──

    private JPanel createDetailCardPanel() {
        detailCardLayout = new CardLayout();
        JPanel panel = new JPanel(detailCardLayout);

        noSelectionLabel = new JLabel("", SwingConstants.CENTER);
        panel.add(noSelectionLabel, "noSelection");

        JPanel detailFormPanel = buildDiscoveryDetailForm();
        panel.add(detailFormPanel, "detailForm");

        return panel;
    }

    private JPanel buildDiscoveryDetailForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.6; gbc.weighty = 1.0;
        panel.add(buildEventDetailsPanel(), gbc);

        gbc.gridx = 1; gbc.weightx = 0.4;
        panel.add(buildRevenueSplitPanel(), gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.weightx = 1.0;
        gbc.weighty = 0; gbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(buildCashierCodePanel(), gbc);

        return panel;
    }

    private JPanel buildEventDetailsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        discoveryDetailsBorder = BorderFactory.createTitledBorder("");
        panel.setBorder(discoveryDetailsBorder);
        GridBagConstraints gbc = createDefaultGbc();

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        discoveryEventNameStaticLabel = new JLabel();
        panel.add(discoveryEventNameStaticLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        eventNameField = new JTextField(20);
        eventNameField.setEditable(false);
        panel.add(eventNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        discoveryEventDescStaticLabel = new JLabel();
        panel.add(discoveryEventDescStaticLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        eventDescriptionField = new JTextArea(3, 20);
        eventDescriptionField.setEditable(false);
        panel.add(new JScrollPane(eventDescriptionField), gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        discoveryEventAddressStaticLabel = new JLabel();
        panel.add(discoveryEventAddressStaticLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        eventAddressField = new JTextField(20);
        eventAddressField.setEditable(false);
        panel.add(eventAddressField, gbc);

        return panel;
    }

    private JPanel buildRevenueSplitPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        discoveryRevenueSplitBorder = BorderFactory.createTitledBorder("");
        panel.setBorder(discoveryRevenueSplitBorder);
        GridBagConstraints gbc = createDefaultGbc();

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        discoveryMarketOwnerStaticLabel = new JLabel();
        panel.add(discoveryMarketOwnerStaticLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        marketOwnerSplitField = new JTextField(5);
        marketOwnerSplitField.setEditable(false);
        panel.add(marketOwnerSplitField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        discoveryVendorStaticLabel = new JLabel();
        panel.add(discoveryVendorStaticLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        vendorSplitField = new JTextField(5);
        vendorSplitField.setEditable(false);
        panel.add(vendorSplitField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        discoveryPlatformStaticLabel = new JLabel();
        panel.add(discoveryPlatformStaticLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        platformSplitField = new JTextField(5);
        platformSplitField.setEditable(false);
        panel.add(platformSplitField, gbc);

        return panel;
    }

    private JPanel buildCashierCodePanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        cashierCodeLabel = new JLabel();
        cashierCodeField = new JTextField(8);
        TextFilters.install(cashierCodeField, new TextFilters.AlnumDashUpperFilter(16));
        panel.add(cashierCodeLabel);
        panel.add(cashierCodeField);
        return panel;
    }

    // ── Active event panel ──

    private JPanel buildActiveEventPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        selectedEventBorder = BorderFactory.createTitledBorder("");
        panel.setBorder(selectedEventBorder);
        GridBagConstraints gbc = createDefaultGbc();

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(createEventDetailsPanel(), gbc);

        gbc.gridy = 1;
        panel.add(createRevenueSplitPanel(), gbc);

        gbc.gridy = 2;
        panel.add(createChangeEventButton(), gbc);

        return panel;
    }

    private JPanel createEventDetailsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        detailsBorder = BorderFactory.createTitledBorder("");
        panel.setBorder(detailsBorder);
        GridBagConstraints gbc = createDefaultGbc();

        gbc.gridx = 0; gbc.gridy = 0;
        eventNameStaticLabel = new JLabel();
        panel.add(eventNameStaticLabel, gbc);
        gbc.gridx = 1;
        activeEventNameLabel = new JLabel("???");
        panel.add(activeEventNameLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        eventDescStaticLabel = new JLabel();
        panel.add(eventDescStaticLabel, gbc);
        gbc.gridx = 1;
        activeEventDescLabel = new JLabel("???");
        panel.add(activeEventDescLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        eventAddressStaticLabel = new JLabel();
        panel.add(eventAddressStaticLabel, gbc);
        gbc.gridx = 1;
        activeEventAddressLabel = new JLabel("???");
        panel.add(activeEventAddressLabel, gbc);

        return panel;
    }

    private JPanel createRevenueSplitPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        revenueSplitBorder = BorderFactory.createTitledBorder("");
        panel.setBorder(revenueSplitBorder);
        GridBagConstraints gbc = createDefaultGbc();

        gbc.gridx = 0; gbc.gridy = 0;
        marketOwnerStaticLabel = new JLabel();
        panel.add(marketOwnerStaticLabel, gbc);
        gbc.gridx = 1;
        marketOwnerSplitLabel = new JLabel("???");
        panel.add(marketOwnerSplitLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        vendorStaticLabel = new JLabel();
        panel.add(vendorStaticLabel, gbc);
        gbc.gridx = 1;
        vendorSplitLabel = new JLabel("???");
        panel.add(vendorSplitLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        platformStaticLabel = new JLabel();
        panel.add(platformStaticLabel, gbc);
        gbc.gridx = 1;
        platformSplitLabel = new JLabel("???");
        panel.add(platformSplitLabel, gbc);

        return panel;
    }

    private JPanel createChangeEventButton() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 100));
        changeEventButton = new JButton();
        changeEventButton.addActionListener(e -> controller.changeEventRequested());
        panel.add(changeEventButton);
        return panel;
    }

    // ── Helpers ──

    private GridBagConstraints createDefaultGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        return gbc;
    }

    private void setEventTexts(TitledBorder dBorder, TitledBorder rBorder,
                               JLabel name, JLabel desc, JLabel addr,
                               JLabel mOwner, JLabel vendor, JLabel platform) {
        dBorder.setTitle(LocalizationManager.tr("event.details.title"));
        rBorder.setTitle(LocalizationManager.tr("revenue_split.title"));
        name.setText(LocalizationManager.tr("event.name"));
        desc.setText(LocalizationManager.tr("event.description"));
        addr.setText(LocalizationManager.tr("event.address"));
        mOwner.setText(LocalizationManager.tr("revenue_split.market_owner"));
        vendor.setText(LocalizationManager.tr("revenue_split.vendor"));
        platform.setText(LocalizationManager.tr("revenue_split.platform"));
    }

    private String getCashierCode() {
        return cashierCodeField.getText().trim();
    }

    // ── DiscoveryPanelInterface ──

    @Override
    public void clearEventsTable() {
        onlineEventsTableModel.setRowCount(0);
        detailCardLayout.show(detailCardPanel, "noSelection");
    }

    @Override
    public void populateEventsTable(List<V1Event> events) {
        onlineEventsTableModel.setRowCount(0);
        for (V1Event ev : events) {
            // Skip local events — those belong in LocalDiscoveryTabPanel
            if (ev.getId() != null && ev.getId().startsWith("local-")) continue;

            onlineEventsTableModel.addRow(new Object[]{
                    ev.getId(),
                    ev.getName(),
                    (ev.getAddressCity() == null ? "" : ev.getAddressCity()),
                    ev.getStartTime(),
                    ev.getEndTime()
            });
        }
    }

    @Override
    public void selectEventById(String eventId) {
        if (eventId == null || eventId.isBlank()) return;
        for (int i = 0; i < onlineEventsTableModel.getRowCount(); i++) {
            if (eventId.equals(onlineEventsTableModel.getValueAt(i, 0))) {
                onlineEventsTable.setRowSelectionInterval(i, i);
                onlineEventsTable.scrollRectToVisible(onlineEventsTable.getCellRect(i, 0, true));
                return;
            }
        }
    }

    @Override
    public void showDetailForm(boolean show) {
        detailCardLayout.show(detailCardPanel, show ? "detailForm" : "noSelection");
    }

    @Override public void setEventName(String name) { eventNameField.setText(name); }
    @Override public void setEventDescription(String desc) { eventDescriptionField.setText(desc != null ? desc : ""); }
    @Override public void setEventAddress(String addr) { eventAddressField.setText(addr); }

    @Override
    public void setLocalMode(boolean local) {
        // In iLoppis mode the fields are always read-only; cashier code is always visible
        cashierCodeLabel.setVisible(true);
        cashierCodeField.setVisible(true);
        eventNameField.setEditable(false);
        eventDescriptionField.setEditable(false);
        eventAddressField.setEditable(false);
    }

    @Override
    public void setRevenueSplitEditable(boolean editable) {
        marketOwnerSplitField.setEditable(editable);
        vendorSplitField.setEditable(editable);
        platformSplitField.setEditable(editable);
    }

    @Override
    public void setRevenueSplit(float marketOwner, float vendor, float platform) {
        marketOwnerSplitField.setText(String.format(Locale.US, "%.0f", marketOwner));
        vendorSplitField.setText(String.format(Locale.US, "%.0f", vendor));
        platformSplitField.setText(String.format(Locale.US, "%.0f", platform));
    }

    @Override public void setCashierButtonEnabled(boolean enabled) { getTokenButton.setEnabled(enabled); }

    @Override
    public void clearCashierCodeField() {
        cashierCodeField.setText("******");
    }

    @Override
    public void setRegisterOpened(boolean opened) {
        rootCardLayout.show(rootCardPanel, opened ? "activeEvent" : "discoveryMode");
    }

    @Override
    public void showActiveEventInfo(V1Event event, V1RevenueSplit split) {
        if (event == null) return;
        activeEventNameLabel.setText(event.getName());
        activeEventDescLabel.setText(event.getDescription());
        activeEventAddressLabel.setText(event.getAddressStreet() + ", " + event.getAddressCity());
        marketOwnerSplitLabel.setText(String.format(Locale.US, "%.0f", split.getMarketOwnerPercentage()));
        vendorSplitLabel.setText(String.format(Locale.US, "%.0f", split.getVendorPercentage()));
        platformSplitLabel.setText(String.format(Locale.US, "%.0f", split.getPlatformProviderPercentage()));
        rootCardLayout.show(rootCardPanel, "activeEvent");
    }

    @Override
    public void setChangeEventButtonVisible(boolean visible) { changeEventButton.setVisible(visible); }

    @Override
    public void selected() { controller.initUIState(); }

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
        dateFromLabel.setText(LocalizationManager.tr("discovery.date_from"));
        discoverButton.setText(LocalizationManager.tr("discovery.fetch_events"));
        getTokenButton.setText(LocalizationManager.tr("discovery.open_register"));

        // Online table headers
        onlineEventsTableModel.setColumnIdentifiers(new String[]{
                LocalizationManager.tr("discovery.table.id_hidden"),
                LocalizationManager.tr("discovery.table.event"),
                LocalizationManager.tr("discovery.table.city"),
                LocalizationManager.tr("discovery.table.opens"),
                LocalizationManager.tr("discovery.table.closes")
        });
        onlineEventsTable.removeColumn(onlineEventsTable.getColumnModel().getColumn(0));

        noSelectionLabel.setText(LocalizationManager.tr("discovery.no_selection"));

        // Discovery detail form
        setEventTexts(discoveryDetailsBorder, discoveryRevenueSplitBorder,
                discoveryEventNameStaticLabel, discoveryEventDescStaticLabel,
                discoveryEventAddressStaticLabel, discoveryMarketOwnerStaticLabel,
                discoveryVendorStaticLabel, discoveryPlatformStaticLabel);

        cashierCodeLabel.setText(LocalizationManager.tr("cashier.code"));

        // Active event panel
        selectedEventBorder.setTitle(LocalizationManager.tr("discovery.selected_event.title"));
        setEventTexts(detailsBorder, revenueSplitBorder,
                eventNameStaticLabel, eventDescStaticLabel, eventAddressStaticLabel,
                marketOwnerStaticLabel, vendorStaticLabel, platformStaticLabel);

        changeEventButton.setText(LocalizationManager.tr("button.change_event"));

        revalidate();
        repaint();
    }

    @Override
    public se.goencoder.loppiskassan.model.BulkUploadResult showBulkUploadDialog(se.goencoder.loppiskassan.storage.LocalEvent localEvent) {
        // Find the Frame parent for the dialog
        Frame parentFrame = null;
        for (Frame frame : Frame.getFrames()) {
            if (frame.isDisplayable() && frame.getName().equals("MainFrame")) {
                parentFrame = frame;
                break;
            }
        }
        if (parentFrame == null && Frame.getFrames().length > 0) {
            parentFrame = Frame.getFrames()[0];
        }

        // Show upload dialog
        se.goencoder.loppiskassan.ui.dialogs.BulkUploadDialog dialog = 
            new se.goencoder.loppiskassan.ui.dialogs.BulkUploadDialog(parentFrame, localEvent);
        return dialog.showDialog();
    }

}
