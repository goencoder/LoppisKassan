package se.goencoder.loppiskassan.ui;

import se.goencoder.iloppis.model.V1Event;
import se.goencoder.iloppis.model.V1RevenueSplit;
import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.controller.CsvExportController;
import se.goencoder.loppiskassan.controller.DiscoveryControllerInterface;
import se.goencoder.loppiskassan.controller.DiscoveryTabController;
import se.goencoder.loppiskassan.controller.ExportLocalEventController;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.utils.LocalEventUtils;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;
import java.util.Locale;

/**
 * Discovery tab for <b>Local</b> mode.
 * Shows only local events — no iLoppis / online section, no upload button.
 * Provides JSONL export (for importing into other LoppisKassan instances)
 * and CSV export (for spreadsheets).
 */
public class LocalDiscoveryTabPanel extends JPanel implements DiscoveryPanelInterface, LocalizationAware {

    private final DiscoveryControllerInterface controller;

    // Root card switching between discovery and active event
    private final CardLayout rootCardLayout;
    private final JPanel rootCardPanel;

    // Local events table
    private JTable localEventsTable;
    private DefaultTableModel localEventsTableModel;
    private JLabel localSectionLabel;
    private JButton createLocalEventButton;

    // Detail card
    private CardLayout detailCardLayout;
    private JPanel detailCardPanel;
    private JLabel noSelectionLabel;

    // Detail form fields
    private JTextField eventNameField;
    private JTextArea eventDescriptionField;
    private JTextField eventAddressField;
    private JTextField marketOwnerSplitField;
    private JTextField vendorSplitField;
    private JTextField platformSplitField;
    private JButton saveLocalEventButton;
    private JButton getTokenButton;

    private TitledBorder discoveryDetailsBorder;
    private TitledBorder discoveryRevenueSplitBorder;
    private JLabel discoveryEventNameStaticLabel;
    private JLabel discoveryEventDescStaticLabel;
    private JLabel discoveryEventAddressStaticLabel;
    private JLabel discoveryMarketOwnerStaticLabel;
    private JLabel discoveryVendorStaticLabel;
    private JLabel discoveryPlatformStaticLabel;

    // Active event view
    private JLabel activeEventNameLabel;
    private JLabel activeEventDescLabel;
    private JLabel activeEventAddressLabel;
    private JButton changeEventButton;
    private JButton exportDataButton;
    private JButton csvExportButton;
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

    public LocalDiscoveryTabPanel() {
        controller = DiscoveryTabController.getInstance();
        controller.registerView(this);

        setLayout(new BorderLayout());
        rootCardLayout = new CardLayout();
        rootCardPanel = new JPanel(rootCardLayout);
        add(rootCardPanel, BorderLayout.CENTER);

        JPanel discoveryPanel = buildDiscoveryPanel();
        JPanel activePanel = buildActiveEventPanel();
        rootCardPanel.add(discoveryPanel, "discoveryMode");
        rootCardPanel.add(activePanel, "activeEvent");

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

    private JPanel buildDiscoveryPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // Header with section label + create button
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        localSectionLabel = new JLabel();
        localSectionLabel.setFont(localSectionLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(localSectionLabel);
        createLocalEventButton = new JButton();
        headerPanel.add(createLocalEventButton);
        panel.add(headerPanel, BorderLayout.NORTH);

        // Table + detail split pane
        saveLocalEventButton = new JButton();
        saveLocalEventButton.setVisible(false);

        localEventsTable = createLocalEventsTable();
        JScrollPane tableScroll = new JScrollPane(localEventsTable);
        tableScroll.setPreferredSize(new Dimension(600, 160));

        detailCardPanel = createDetailCardPanel();

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, detailCardPanel);
        splitPane.setResizeWeight(0.45);
        panel.add(splitPane, BorderLayout.CENTER);

        // Bottom: Open Register
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        getTokenButton = new JButton();
        bottomPanel.add(getTokenButton);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        // Listeners
        createLocalEventButton.addActionListener(e -> {
            CreateLocalEventDialog dialog = new CreateLocalEventDialog(SwingUtilities.getWindowAncestor(this));
            se.goencoder.loppiskassan.storage.LocalEvent created = dialog.showDialog();
            if (created != null) {
                controller.discoverEvents(null);
                selectEventById(created.getEventId());
            }
        });

        getTokenButton.addActionListener(e -> {
            int rowIndex = getSelectedTableRow();
            String eventId = getEventIdForRow(rowIndex);
            controller.openRegister(eventId, "");
        });

        saveLocalEventButton.addActionListener(e -> {
            int rowIndex = getSelectedTableRow();
            String eventId = getEventIdForRow(rowIndex);
            if (eventId == null || eventId.isBlank()) return;
            controller.saveLocalEventEdits(eventId,
                    eventNameField.getText().trim(),
                    eventDescriptionField.getText().trim(),
                    eventAddressField.getText().trim(),
                    parsePercentage(marketOwnerSplitField.getText()),
                    parsePercentage(vendorSplitField.getText()),
                    parsePercentage(platformSplitField.getText()));
        });

        return panel;
    }

    private JTable createLocalEventsTable() {
        String[] cols = {
            LocalizationManager.tr("discovery.table.id_hidden"),
            LocalizationManager.tr("discovery.table.local.name"),
            LocalizationManager.tr("discovery.table.local.created"),
            LocalizationManager.tr("discovery.table.local.sales"),
            LocalizationManager.tr("discovery.table.local.status")
        };
        localEventsTableModel = new DefaultTableModel(new Object[][]{}, cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        JTable table = new JTable(localEventsTableModel);
        table.removeColumn(table.getColumnModel().getColumn(0));

        table.getSelectionModel().addListSelectionListener((ListSelectionEvent e) -> {
            if (!e.getValueIsAdjusting()) {
                int row = table.getSelectedRow();
                if (row >= 0) {
                    String eventId = (String) localEventsTableModel.getValueAt(row, 0);
                    controller.eventSelected(eventId);
                }
            }
        });

        // Click in status column triggers JSONL export
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row >= 0 && col == 3) { // Status column after hiding ID
                    String eventId = (String) localEventsTableModel.getValueAt(row, 0);
                    if (eventId != null && LocalEventUtils.getSalesCount(eventId) > 0) {
                        String name = (String) localEventsTableModel.getValueAt(row, 1);
                        ExportLocalEventController.exportEventData(eventId, name);
                    }
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
        panel.add(buildDetailForm(), "detailForm");
        return panel;
    }

    private JPanel buildDetailForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
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
        // Save button row (no cashier code for local mode)
        JPanel savePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        savePanel.add(saveLocalEventButton);
        panel.add(savePanel, gbc);

        return panel;
    }

    private JPanel buildEventDetailsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        discoveryDetailsBorder = javax.swing.BorderFactory.createTitledBorder("");
        panel.setBorder(discoveryDetailsBorder);
        GridBagConstraints gbc = defaultGbc();

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        discoveryEventNameStaticLabel = new JLabel();
        panel.add(discoveryEventNameStaticLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        eventNameField = new JTextField(20);
        panel.add(eventNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        discoveryEventDescStaticLabel = new JLabel();
        panel.add(discoveryEventDescStaticLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        eventDescriptionField = new JTextArea(3, 20);
        panel.add(new JScrollPane(eventDescriptionField), gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        discoveryEventAddressStaticLabel = new JLabel();
        panel.add(discoveryEventAddressStaticLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        eventAddressField = new JTextField(20);
        panel.add(eventAddressField, gbc);

        return panel;
    }

    private JPanel buildRevenueSplitPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        discoveryRevenueSplitBorder = javax.swing.BorderFactory.createTitledBorder("");
        panel.setBorder(discoveryRevenueSplitBorder);
        GridBagConstraints gbc = defaultGbc();

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        discoveryMarketOwnerStaticLabel = new JLabel();
        panel.add(discoveryMarketOwnerStaticLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        marketOwnerSplitField = new JTextField(5);
        TextFilters.install(marketOwnerSplitField, new TextFilters.DigitsOnlyFilter(3));
        panel.add(marketOwnerSplitField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        discoveryVendorStaticLabel = new JLabel();
        panel.add(discoveryVendorStaticLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        vendorSplitField = new JTextField(5);
        TextFilters.install(vendorSplitField, new TextFilters.DigitsOnlyFilter(3));
        panel.add(vendorSplitField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        discoveryPlatformStaticLabel = new JLabel();
        panel.add(discoveryPlatformStaticLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        platformSplitField = new JTextField(5);
        TextFilters.install(platformSplitField, new TextFilters.DigitsOnlyFilter(3));
        panel.add(platformSplitField, gbc);

        return panel;
    }

    // ── Active event panel ──

    private JPanel buildActiveEventPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        selectedEventBorder = javax.swing.BorderFactory.createTitledBorder("");
        panel.setBorder(selectedEventBorder);
        GridBagConstraints gbc = defaultGbc();

        gbc.gridx = 0; gbc.gridy = 0;
        panel.add(createActiveDetailsPanel(), gbc);

        gbc.gridy = 1;
        panel.add(createActiveRevenueSplitPanel(), gbc);

        gbc.gridy = 2;
        panel.add(createActionButtonsPanel(), gbc);

        return panel;
    }

    private JPanel createActiveDetailsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        detailsBorder = javax.swing.BorderFactory.createTitledBorder("");
        panel.setBorder(detailsBorder);
        GridBagConstraints gbc = defaultGbc();

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

    private JPanel createActiveRevenueSplitPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        revenueSplitBorder = javax.swing.BorderFactory.createTitledBorder("");
        panel.setBorder(revenueSplitBorder);
        GridBagConstraints gbc = defaultGbc();

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

    private JPanel createActionButtonsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 20));

        exportDataButton = new JButton();
        exportDataButton.setVisible(false);
        exportDataButton.setToolTipText(LocalizationManager.tr("export.button.tooltip.jsonl"));
        exportDataButton.addActionListener(e -> {
            String eventId = ConfigurationStore.EVENT_ID_STR.get();
            if (eventId != null && eventId.startsWith("local-")) {
                ExportLocalEventController.exportEventData(eventId, activeEventNameLabel.getText());
            }
        });
        panel.add(exportDataButton);

        csvExportButton = new JButton();
        csvExportButton.setVisible(false);
        csvExportButton.setToolTipText(LocalizationManager.tr("export.button.tooltip.csv"));
        csvExportButton.addActionListener(e -> {
            String eventId = ConfigurationStore.EVENT_ID_STR.get();
            if (eventId != null && eventId.startsWith("local-")) {
                CsvExportController.exportEventDataAsCsv(eventId, activeEventNameLabel.getText());
            }
        });
        panel.add(csvExportButton);

        changeEventButton = new JButton();
        changeEventButton.addActionListener(e -> controller.changeEventRequested());
        panel.add(changeEventButton);

        return panel;
    }

    // ── Helpers ──

    private GridBagConstraints defaultGbc() {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 5, 5);
        return gbc;
    }

    private int getSelectedTableRow() {
        return localEventsTable.getSelectedRow();
    }

    private String getEventIdForRow(int rowIndex) {
        if (rowIndex < 0) return null;
        return (String) localEventsTableModel.getValueAt(rowIndex, 0);
    }

    private float parsePercentage(String value) {
        if (value == null || value.isBlank()) return 0f;
        try { return Float.parseFloat(value); }
        catch (NumberFormatException ex) { return 0f; }
    }

    private String getCashierCode() { return ""; }

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

    // ── DiscoveryPanelInterface implementation ──

    @Override public void clearEventsTable() {
        localEventsTableModel.setRowCount(0);
        detailCardLayout.show(detailCardPanel, "noSelection");
    }

    @Override public void populateEventsTable(List<V1Event> events) {
        localEventsTableModel.setRowCount(0);
        for (V1Event ev : events) {
            if (ev.getId() == null || !ev.getId().startsWith("local-")) continue;
            int sales = LocalEventUtils.getSalesCount(ev.getId());
            String salesText = sales > 0 ? sales + " köp" : "—";
            String status = LocalEventUtils.getLocalEventStatusText(ev.getId(), false);
            if (sales > 0) status += " 📤";
            localEventsTableModel.addRow(new Object[]{
                    ev.getId(), ev.getName(),
                    ev.getStartTime() != null ? ev.getStartTime().toLocalDate().toString() : "",
                    salesText, status
            });
        }
    }

    @Override public void selectEventById(String eventId) {
        if (eventId == null || eventId.isBlank()) return;
        for (int i = 0; i < localEventsTableModel.getRowCount(); i++) {
            if (eventId.equals(localEventsTableModel.getValueAt(i, 0))) {
                localEventsTable.setRowSelectionInterval(i, i);
                localEventsTable.scrollRectToVisible(localEventsTable.getCellRect(i, 0, true));
                return;
            }
        }
    }

    @Override public void showDetailForm(boolean show) {
        detailCardLayout.show(detailCardPanel, show ? "detailForm" : "noSelection");
    }

    @Override public void setEventName(String name) { eventNameField.setText(name); }
    @Override public void setEventDescription(String desc) { eventDescriptionField.setText(desc != null ? desc : ""); }
    @Override public void setEventAddress(String addr) { eventAddressField.setText(addr); }

    @Override public void setLocalMode(boolean local) {
        // Always local in this panel
        eventNameField.setEditable(true);
        eventDescriptionField.setEditable(true);
        eventAddressField.setEditable(true);
        saveLocalEventButton.setVisible(true);
    }

    @Override public void setRevenueSplitEditable(boolean editable) {
        marketOwnerSplitField.setEditable(editable);
        vendorSplitField.setEditable(editable);
        platformSplitField.setEditable(editable);
    }

    @Override public void setRevenueSplit(float mo, float v, float p) {
        marketOwnerSplitField.setText(String.format(Locale.US, "%.0f", mo));
        vendorSplitField.setText(String.format(Locale.US, "%.0f", v));
        platformSplitField.setText(String.format(Locale.US, "%.0f", p));
    }

    @Override public void setCashierButtonEnabled(boolean enabled) { getTokenButton.setEnabled(enabled); }
    @Override public void clearCashierCodeField() { /* no-op in local mode */ }

    @Override public void setRegisterOpened(boolean opened) {
        rootCardLayout.show(rootCardPanel, opened ? "activeEvent" : "discoveryMode");
    }

    @Override public void showActiveEventInfo(V1Event event, V1RevenueSplit split) {
        if (event == null) return;
        activeEventNameLabel.setText(event.getName());
        activeEventDescLabel.setText(event.getDescription());
        activeEventAddressLabel.setText(event.getAddressStreet() + ", " + event.getAddressCity());
        marketOwnerSplitLabel.setText(String.format(Locale.US, "%.0f", split.getMarketOwnerPercentage()));
        vendorSplitLabel.setText(String.format(Locale.US, "%.0f", split.getVendorPercentage()));
        platformSplitLabel.setText(String.format(Locale.US, "%.0f", split.getPlatformProviderPercentage()));

        // Export buttons: visible for local events with sales (no upload in local mode)
        boolean hasSales = event.getId() != null && event.getId().startsWith("local-")
                && LocalEventUtils.getSalesCount(event.getId()) > 0;
        exportDataButton.setVisible(hasSales);
        csvExportButton.setVisible(hasSales);

        rootCardLayout.show(rootCardPanel, "activeEvent");
    }

    @Override public void setChangeEventButtonVisible(boolean visible) { changeEventButton.setVisible(visible); }

    @Override public void selected() { controller.initUIState(); }

    @Override public void addNotify() {
        super.addNotify();
        LocalizationManager.addListener(this::reloadTexts);
    }

    @Override public void removeNotify() {
        LocalizationManager.removeListener(this::reloadTexts);
        super.removeNotify();
    }

    @Override
    public void reloadTexts() {
        localSectionLabel.setText(LocalizationManager.tr("discovery.section.local"));
        createLocalEventButton.setText(LocalizationManager.tr("discovery.create_local_event"));
        getTokenButton.setText(LocalizationManager.tr("discovery.open_register"));
        saveLocalEventButton.setText(LocalizationManager.tr("discovery.save_local_event"));

        localEventsTableModel.setColumnIdentifiers(new String[]{
                LocalizationManager.tr("discovery.table.id_hidden"),
                LocalizationManager.tr("discovery.table.local.name"),
                LocalizationManager.tr("discovery.table.local.created"),
                LocalizationManager.tr("discovery.table.local.sales"),
                LocalizationManager.tr("discovery.table.local.status")
        });
        localEventsTable.removeColumn(localEventsTable.getColumnModel().getColumn(0));

        noSelectionLabel.setText(LocalizationManager.tr("discovery.no_selection"));

        setEventTexts(discoveryDetailsBorder, discoveryRevenueSplitBorder,
                discoveryEventNameStaticLabel, discoveryEventDescStaticLabel,
                discoveryEventAddressStaticLabel, discoveryMarketOwnerStaticLabel,
                discoveryVendorStaticLabel, discoveryPlatformStaticLabel);

        selectedEventBorder.setTitle(LocalizationManager.tr("discovery.selected_event.title"));
        setEventTexts(detailsBorder, revenueSplitBorder,
                eventNameStaticLabel, eventDescStaticLabel, eventAddressStaticLabel,
                marketOwnerStaticLabel, vendorStaticLabel, platformStaticLabel);

        changeEventButton.setText(LocalizationManager.tr("button.change_event"));
        exportDataButton.setText(LocalizationManager.tr("export.button.export_data"));
        csvExportButton.setText(LocalizationManager.tr("export.button.export_csv"));

        if (ConfigurationStore.LOCAL_EVENT_BOOL.getBooleanValueOrDefault(false)) {
            activeEventNameLabel.setText(LocalizationManager.tr("event.local.name"));
            activeEventDescLabel.setText(LocalizationManager.tr("event.local.description"));
            activeEventAddressLabel.setText(
                    LocalizationManager.tr("event.no_street") + ", " + LocalizationManager.tr("event.no_city"));
            String eventId = ConfigurationStore.EVENT_ID_STR.get();
            if (eventId != null && eventId.startsWith("local-")) {
                boolean hasSales = LocalEventUtils.getSalesCount(eventId) > 0;
                exportDataButton.setVisible(hasSales);
                csvExportButton.setVisible(hasSales);
            }
        }

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
