package se.goencoder.loppiskassan.ui;

import se.goencoder.iloppis.model.V1Event;
import se.goencoder.iloppis.model.V1RevenueSplit;
import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.controller.CsvExportController;
import se.goencoder.loppiskassan.controller.DiscoveryControllerInterface;
import se.goencoder.loppiskassan.controller.DiscoveryTabController;
import se.goencoder.loppiskassan.controller.ExportLocalEventController;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.utils.LocalEventUtils;
import se.goencoder.loppiskassan.util.SwedishDateFormatter;

import javax.swing.*;
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

    // Local events card grid
    private JPanel eventCardsGrid;
    private JScrollPane cardsScrollPane;
    private EventCard selectedCard;
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
    private JButton deleteEventButton;
    private JPanel expandedDetailsPanel;

    private JLabel discoveryDetailsHeaderLabel;
    private JLabel discoveryRevenueSplitHeaderLabel;
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
    private ProvisionBar activeProvisionBar;

    private JLabel selectedEventHeaderLabel;
    private JLabel detailsHeaderLabel;
    private JLabel revenueSplitHeaderLabel;
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
        String savedEventId = AppModeManager.getEventId();
        if (savedEventId == null || savedEventId.isEmpty()) {
            rootCardLayout.show(rootCardPanel, "discoveryMode");
        }
        controller.initUIState();
    }

    // ── Discovery mode panel ──

    private JPanel buildDiscoveryPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 16));
        panel.setBackground(AppColors.WHITE);

        // Header with section label + create button
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 8));
        headerPanel.setBackground(AppColors.WHITE);
        headerPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        localSectionLabel = new JLabel();
        localSectionLabel.setFont(localSectionLabel.getFont().deriveFont(Font.BOLD, 18f));
        headerPanel.add(localSectionLabel);
        createLocalEventButton = new JButton();
        AppButton.applyStyle(createLocalEventButton, AppButton.Variant.SECONDARY, AppButton.Size.MEDIUM);
        headerPanel.add(createLocalEventButton);
        panel.add(headerPanel, BorderLayout.NORTH);

        // Card grid for events (FlowLayout wraps automatically)
        eventCardsGrid = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 16));
        eventCardsGrid.setBackground(AppColors.WHITE);
        eventCardsGrid.setBorder(BorderFactory.createEmptyBorder(8, 16, 16, 16));
        cardsScrollPane = new JScrollPane(eventCardsGrid);
        cardsScrollPane.setBorder(null);
        cardsScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        panel.add(cardsScrollPane, BorderLayout.CENTER);

        // Expanded details panel (collapsible, only shown when card selected)
        expandedDetailsPanel = buildExpandedDetailsPanel();
        expandedDetailsPanel.setVisible(false);
        panel.add(expandedDetailsPanel, BorderLayout.SOUTH);

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
            if (selectedCard != null) {
                controller.openRegister(selectedCard.eventId, "");
            }
        });

        saveLocalEventButton.addActionListener(e -> {
            if (selectedCard == null) return;
            String eventId = selectedCard.eventId;
            controller.saveLocalEventEdits(eventId,
                    eventNameField.getText().trim(),
                    eventDescriptionField.getText().trim(),
                    eventAddressField.getText().trim(),
                    parsePercentage(marketOwnerSplitField.getText()),
                    parsePercentage(vendorSplitField.getText()),
                    parsePercentage(platformSplitField.getText()));
        });

        deleteEventButton.addActionListener(e -> {
            if (selectedCard == null) return;
            handleDeleteEvent(selectedCard.eventId, selectedCard.eventName);
        });

        return panel;
    }

    private JPanel buildExpandedDetailsPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 20));
        panel.setBackground(AppColors.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, AppColors.BORDER),
            BorderFactory.createEmptyBorder(24, 32, 24, 32)
        ));

        // Detail card (no selection / form)
        detailCardPanel = createDetailCardPanel();
        panel.add(detailCardPanel, BorderLayout.CENTER);

        // Buttons at bottom: Delete (left), Save + Start (right)
        JPanel buttonsPanel = new JPanel(new BorderLayout());
        buttonsPanel.setBackground(AppColors.WHITE);

        deleteEventButton = new JButton();
        AppButton.applyStyle(deleteEventButton, AppButton.Variant.DANGER, AppButton.Size.MEDIUM);
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        leftButtons.setBackground(AppColors.WHITE);
        leftButtons.add(deleteEventButton);
        buttonsPanel.add(leftButtons, BorderLayout.WEST);

        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 0));
        rightButtons.setBackground(AppColors.WHITE);
        
        saveLocalEventButton = new JButton();
        saveLocalEventButton.setVisible(false);
        AppButton.applyStyle(saveLocalEventButton, AppButton.Variant.OUTLINE, AppButton.Size.MEDIUM);
        rightButtons.add(saveLocalEventButton);

        getTokenButton = new JButton();
        AppButton.applyStyle(getTokenButton, AppButton.Variant.PRIMARY, AppButton.Size.MEDIUM);
        rightButtons.add(getTokenButton);
        buttonsPanel.add(rightButtons, BorderLayout.EAST);

        panel.add(buttonsPanel, BorderLayout.SOUTH);
        return panel;
    }

    private void handleDeleteEvent(String eventId, String eventName) {
        int confirm = JOptionPane.showConfirmDialog(
            SwingUtilities.getWindowAncestor(this),
            String.format(LocalizationManager.tr("discovery.delete.confirm"), eventName),
            LocalizationManager.tr("discovery.delete.title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (confirm == JOptionPane.YES_OPTION) {
            controller.deleteLocalEvent(eventId);
            controller.discoverEvents(null);
            selectedCard = null;
            expandedDetailsPanel.setVisible(false);
        }
    }

    /**
     * EventCard - A card component representing a local event.
     * Click to select, double-click to open register.
     */
    private class EventCard extends JPanel {
        private final String eventId;
        private final String eventName;
        private final JLabel nameLabel;
        private final JLabel dateLabel;
        private final JLabel salesLabel;
        private final JLabel statusBadge;
        private boolean hovered = false;
        private boolean isSelected = false;
        
        private static final Color CARD_BG = AppColors.FIELD_BG;
        private static final Color CARD_HOVER_BORDER = AppColors.ACCENT;
        private static final Color CARD_SELECTED_BG = AppColors.SELECTED_BG;
        private static final Color CARD_SELECTED_ACCENT = AppColors.ACCENT;

        public EventCard(String eventId, String eventName, String dateText, String salesText, String statusText, boolean hasExport) {
            this.eventId = eventId;
            this.eventName = eventName;
            
            setLayout(new BorderLayout(8, 6));
            setOpaque(false); // We paint our own background
            setPreferredSize(new Dimension(260, 120));
            setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
            setCursor(new Cursor(Cursor.HAND_CURSOR));

            // Header: name + export badge
            JPanel headerPanel = new JPanel(new BorderLayout(4, 0));
            headerPanel.setOpaque(false);
            nameLabel = new JLabel(eventName);
            nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD, 14f));
            nameLabel.setForeground(AppColors.TEXT_PRIMARY);
            headerPanel.add(nameLabel, BorderLayout.CENTER);
            
            if (hasExport) {
                statusBadge = new JLabel("📤");
                statusBadge.setToolTipText(LocalizationManager.tr("discovery.card.export_available"));
                headerPanel.add(statusBadge, BorderLayout.EAST);
            } else {
                statusBadge = null;
            }
            add(headerPanel, BorderLayout.NORTH);

            // Body: date + sales + status
            JPanel bodyPanel = new JPanel(new GridLayout(3, 1, 0, 2));
            bodyPanel.setOpaque(false);
            
            dateLabel = new JLabel(dateText);
            dateLabel.setFont(dateLabel.getFont().deriveFont(12f));
            dateLabel.setForeground(AppColors.TEXT_MUTED);
            bodyPanel.add(dateLabel);

            salesLabel = new JLabel(salesText);
            salesLabel.setFont(salesLabel.getFont().deriveFont(Font.BOLD, 12f));
            salesLabel.setForeground(AppColors.TEXT_SECONDARY);
            bodyPanel.add(salesLabel);

            JLabel statusLabel = new JLabel(statusText);
            statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));
            statusLabel.setForeground(AppColors.TEXT_MUTED);
            bodyPanel.add(statusLabel);

            add(bodyPanel, BorderLayout.CENTER);

            // Mouse listener for click, hover and double-click
            java.awt.event.MouseAdapter mouseHandler = new java.awt.event.MouseAdapter() {
                @Override
                public void mousePressed(java.awt.event.MouseEvent e) {
                    // Use mousePressed for reliable click
                    if (e.getClickCount() == 2) {
                        controller.openRegister(eventId, "");
                    } else {
                        selectCard(EventCard.this);
                    }
                }

                @Override
                public void mouseEntered(java.awt.event.MouseEvent e) {
                    hovered = true;
                    repaint();
                }

                @Override
                public void mouseExited(java.awt.event.MouseEvent e) {
                    hovered = false;
                    repaint();
                }
            };
            addMouseListener(mouseHandler);
            // Also listen on children so clicks on labels work
            for (Component child : getComponents()) {
                child.addMouseListener(mouseHandler);
                if (child instanceof JPanel childPanel) {
                    for (Component grandChild : childPanel.getComponents()) {
                        grandChild.addMouseListener(mouseHandler);
                    }
                }
            }
        }

        public void setSelected(boolean selected) {
            this.isSelected = selected;
            repaint();
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            int w = getWidth();
            int h = getHeight();
            int arc = 12;
            
            // Card background
            if (isSelected) {
                g2.setColor(CARD_SELECTED_BG);
            } else {
                g2.setColor(CARD_BG);
            }
            g2.fillRoundRect(0, 0, w, h, arc, arc);
            
            // Border: hover = blue outline, selected = blue left accent
            if (isSelected) {
                // Left accent bar
                g2.setColor(CARD_SELECTED_ACCENT);
                g2.fillRoundRect(0, 0, 4, h, 4, 4);
                // Thin border around whole card
                g2.setColor(AppColors.ACCENT_TRANSLUCENT);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
            } else if (hovered) {
                g2.setColor(CARD_HOVER_BORDER);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(1, 1, w - 3, h - 3, arc, arc);
            }
            
            g2.dispose();
            
            super.paintComponent(g);
        }
    }

    private void selectCard(EventCard card) {
        // Deselect previous card
        if (selectedCard != null && selectedCard != card) {
            selectedCard.setSelected(false);
        }
        
        // Select new card
        selectedCard = card;
        card.setSelected(true);
        
        // Show expanded details panel and populate form
        expandedDetailsPanel.setVisible(true);
        controller.eventSelected(card.eventId);
    }

    // ── Detail card ──

    private JPanel createDetailCardPanel() {
        detailCardLayout = new CardLayout();
        JPanel panel = new JPanel(detailCardLayout);
        panel.setBackground(AppColors.WHITE);
        
        // Empty state
        JPanel emptyPanel = new JPanel(new GridBagLayout());
        emptyPanel.setBackground(AppColors.WHITE);
        noSelectionLabel = new JLabel("", SwingConstants.CENTER);
        noSelectionLabel.setFont(noSelectionLabel.getFont().deriveFont(Font.ITALIC, 13f));
        noSelectionLabel.setForeground(AppColors.TEXT_MUTED);
        emptyPanel.add(noSelectionLabel);
        
        panel.add(emptyPanel, "noSelection");
        panel.add(buildDetailForm(), "detailForm");
        return panel;
    }

    private JPanel buildDetailForm() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(AppColors.WHITE);
        panel.setBorder(null);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 1.0; gbc.weighty = 0;
        panel.add(buildEventDetailsPanel(), gbc);

        gbc.gridy = 1; gbc.weighty = 0;
        panel.add(buildRevenueSplitPanel(), gbc);
        
        // Spacer to push content up
        gbc.gridy = 2; gbc.weighty = 1.0;
        panel.add(Box.createVerticalGlue(), gbc);

        return panel;
    }

    private JPanel buildEventDetailsPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(AppColors.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        discoveryDetailsHeaderLabel = new JLabel(); // Phantom label for localization in setEventTexts
        GridBagConstraints gbc = defaultGbc();

        Color fieldBg = AppColors.FIELD_BG;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        discoveryEventNameStaticLabel = new JLabel();
        panel.add(discoveryEventNameStaticLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        eventNameField = new JTextField(20);
        eventNameField.setBackground(fieldBg);
        panel.add(eventNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        discoveryEventDescStaticLabel = new JLabel();
        panel.add(discoveryEventDescStaticLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        eventDescriptionField = new JTextArea(3, 20);
        eventDescriptionField.setBackground(fieldBg);
        JScrollPane descScroll = new JScrollPane(eventDescriptionField);
        descScroll.getViewport().setBackground(fieldBg);
        panel.add(descScroll, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        discoveryEventAddressStaticLabel = new JLabel();
        panel.add(discoveryEventAddressStaticLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        eventAddressField = new JTextField(20);
        eventAddressField.setBackground(fieldBg);
        panel.add(eventAddressField, gbc);

        return panel;
    }

    private JPanel buildRevenueSplitPanel() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(AppColors.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        discoveryRevenueSplitHeaderLabel = new JLabel(); // Phantom label for localization in setEventTexts
        GridBagConstraints gbc = defaultGbc();

        Color fieldBg = AppColors.FIELD_BG;

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        discoveryMarketOwnerStaticLabel = new JLabel();
        panel.add(discoveryMarketOwnerStaticLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        marketOwnerSplitField = new JTextField(5);
        marketOwnerSplitField.setBackground(fieldBg);
        TextFilters.install(marketOwnerSplitField, new TextFilters.DigitsOnlyFilter(3));
        panel.add(marketOwnerSplitField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; gbc.weightx = 0;
        discoveryVendorStaticLabel = new JLabel();
        panel.add(discoveryVendorStaticLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        vendorSplitField = new JTextField(5);
        vendorSplitField.setBackground(fieldBg);
        TextFilters.install(vendorSplitField, new TextFilters.DigitsOnlyFilter(3));
        panel.add(vendorSplitField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.weightx = 0;
        discoveryPlatformStaticLabel = new JLabel();
        panel.add(discoveryPlatformStaticLabel, gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        platformSplitField = new JTextField(5);
        platformSplitField.setBackground(fieldBg);
        TextFilters.install(platformSplitField, new TextFilters.DigitsOnlyFilter(3));
        panel.add(platformSplitField, gbc);

        return panel;
    }

    // ── Active event panel ──

    private JPanel buildActiveEventPanel() {
        JPanel outerPanel = new JPanel(new GridBagLayout());
        outerPanel.setBackground(AppColors.WHITE);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(AppColors.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(40, 60, 40, 60));

        // Page header
        selectedEventHeaderLabel = new JLabel();
        selectedEventHeaderLabel.setFont(selectedEventHeaderLabel.getFont().deriveFont(Font.BOLD, 20f));
        selectedEventHeaderLabel.setForeground(AppColors.TEXT_PRIMARY);
        selectedEventHeaderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(selectedEventHeaderLabel);
        panel.add(Box.createVerticalStrut(32));

        JPanel detailsSection = createActiveDetailsPanel();
        detailsSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(detailsSection);
        panel.add(Box.createVerticalStrut(28));

        JPanel splitSection = createActiveRevenueSplitPanel();
        splitSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(splitSection);
        panel.add(Box.createVerticalStrut(32));

        JPanel buttonsSection = createActionButtonsPanel();
        buttonsSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(buttonsSection);

        // Center content in viewport
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.CENTER;
        outerPanel.add(panel, gbc);

        return outerPanel;
    }

    private JPanel createActiveDetailsPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(AppColors.WHITE);

        detailsHeaderLabel = new JLabel();
        detailsHeaderLabel.setFont(detailsHeaderLabel.getFont().deriveFont(Font.BOLD, 14f));
        detailsHeaderLabel.setForeground(AppColors.TEXT_SECONDARY);
        detailsHeaderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(detailsHeaderLabel);
        panel.add(Box.createVerticalStrut(4));

        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(AppColors.BORDER);
        panel.add(sep);
        panel.add(Box.createVerticalStrut(12));

        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        fieldsPanel.setBackground(AppColors.WHITE);
        fieldsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = defaultGbc();

        gbc.gridx = 0; gbc.gridy = 0;
        eventNameStaticLabel = new JLabel();
        eventNameStaticLabel.setFont(eventNameStaticLabel.getFont().deriveFont(Font.BOLD));
        fieldsPanel.add(eventNameStaticLabel, gbc);
        gbc.gridx = 1;
        activeEventNameLabel = new JLabel();
        fieldsPanel.add(activeEventNameLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        eventDescStaticLabel = new JLabel();
        eventDescStaticLabel.setFont(eventDescStaticLabel.getFont().deriveFont(Font.BOLD));
        fieldsPanel.add(eventDescStaticLabel, gbc);
        gbc.gridx = 1;
        activeEventDescLabel = new JLabel();
        fieldsPanel.add(activeEventDescLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        eventAddressStaticLabel = new JLabel();
        eventAddressStaticLabel.setFont(eventAddressStaticLabel.getFont().deriveFont(Font.BOLD));
        fieldsPanel.add(eventAddressStaticLabel, gbc);
        gbc.gridx = 1;
        activeEventAddressLabel = new JLabel();
        fieldsPanel.add(activeEventAddressLabel, gbc);

        panel.add(fieldsPanel);
        return panel;
    }

    private JPanel createActiveRevenueSplitPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(AppColors.WHITE);

        revenueSplitHeaderLabel = new JLabel();
        revenueSplitHeaderLabel.setFont(revenueSplitHeaderLabel.getFont().deriveFont(Font.BOLD, 14f));
        revenueSplitHeaderLabel.setForeground(AppColors.TEXT_SECONDARY);
        revenueSplitHeaderLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(revenueSplitHeaderLabel);
        panel.add(Box.createVerticalStrut(4));

        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sep.setForeground(AppColors.BORDER);
        panel.add(sep);
        panel.add(Box.createVerticalStrut(12));

        JPanel fieldsPanel = new JPanel(new GridBagLayout());
        fieldsPanel.setBackground(AppColors.WHITE);
        fieldsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints gbc = defaultGbc();

        gbc.gridx = 0; gbc.gridy = 0;
        marketOwnerStaticLabel = new JLabel();
        marketOwnerStaticLabel.setFont(marketOwnerStaticLabel.getFont().deriveFont(Font.BOLD));
        fieldsPanel.add(marketOwnerStaticLabel, gbc);
        gbc.gridx = 1;
        marketOwnerSplitLabel = new JLabel();
        fieldsPanel.add(marketOwnerSplitLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        vendorStaticLabel = new JLabel();
        vendorStaticLabel.setFont(vendorStaticLabel.getFont().deriveFont(Font.BOLD));
        fieldsPanel.add(vendorStaticLabel, gbc);
        gbc.gridx = 1;
        vendorSplitLabel = new JLabel();
        fieldsPanel.add(vendorSplitLabel, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        platformStaticLabel = new JLabel();
        platformStaticLabel.setFont(platformStaticLabel.getFont().deriveFont(Font.BOLD));
        fieldsPanel.add(platformStaticLabel, gbc);
        gbc.gridx = 1;
        platformSplitLabel = new JLabel();
        fieldsPanel.add(platformSplitLabel, gbc);

        // Provision bar
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(8, 5, 5, 5);
        fieldsPanel.add(createProvisionBar(), gbc);

        panel.add(fieldsPanel);
        return panel;
    }

    /**
     * Creates a visual bar showing provision distribution.
     * Initially empty, updated when event is selected.
     */
    private JPanel createProvisionBar() {
        activeProvisionBar = new ProvisionBar();
        return activeProvisionBar;
    }

    private JPanel createActionButtonsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        panel.setBackground(AppColors.WHITE);

        exportDataButton = new JButton();
        exportDataButton.setVisible(false);
        exportDataButton.setToolTipText(LocalizationManager.tr("export.button.tooltip.jsonl"));
        AppButton.applyStyle(exportDataButton, AppButton.Variant.OUTLINE, AppButton.Size.MEDIUM);
        exportDataButton.addActionListener(e -> {
            String eventId = AppModeManager.getEventId();
            if (eventId != null && eventId.startsWith("local-")) {
                ExportLocalEventController.exportEventData(eventId, activeEventNameLabel.getText());
            }
        });
        panel.add(exportDataButton);

        csvExportButton = new JButton();
        csvExportButton.setVisible(false);
        csvExportButton.setToolTipText(LocalizationManager.tr("export.button.tooltip.csv"));
        AppButton.applyStyle(csvExportButton, AppButton.Variant.OUTLINE, AppButton.Size.MEDIUM);
        csvExportButton.addActionListener(e -> {
            String eventId = AppModeManager.getEventId();
            if (eventId != null && eventId.startsWith("local-")) {
                CsvExportController.exportEventDataAsCsv(eventId, activeEventNameLabel.getText());
            }
        });
        panel.add(csvExportButton);

        changeEventButton = new JButton();
        AppButton.applyStyle(changeEventButton, AppButton.Variant.PRIMARY, AppButton.Size.MEDIUM);
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
        return -1; // Obsolete with card layout
    }

    private String getEventIdForRow(int rowIndex) {
        return null; // Obsolete with card layout
    }

    private float parsePercentage(String value) {
        if (value == null || value.isBlank()) return 0f;
        try { return Float.parseFloat(value); }
        catch (NumberFormatException ex) { return 0f; }
    }

    private String getCashierCode() { return ""; }

    private void setEventTexts(JLabel dHeader, JLabel rHeader,
                               JLabel name, JLabel desc, JLabel addr,
                               JLabel mOwner, JLabel vendor, JLabel platform) {
        dHeader.setText(LocalizationManager.tr("event.details.title"));
        rHeader.setText(LocalizationManager.tr("revenue_split.title"));
        name.setText(LocalizationManager.tr("event.name"));
        desc.setText(LocalizationManager.tr("event.description"));
        addr.setText(LocalizationManager.tr("event.address"));
        mOwner.setText(LocalizationManager.tr("revenue_split.market_owner"));
        vendor.setText(LocalizationManager.tr("revenue_split.vendor"));
        platform.setText(LocalizationManager.tr("revenue_split.platform"));
    }

    // ── DiscoveryPanelInterface implementation ──

    @Override public void clearEventsTable() {
        eventCardsGrid.removeAll();
        selectedCard = null;
        expandedDetailsPanel.setVisible(false);
        eventCardsGrid.revalidate();
        eventCardsGrid.repaint();
    }

    @Override public void populateEventsTable(List<V1Event> events) {
        eventCardsGrid.removeAll();
        selectedCard = null;
        
        for (V1Event ev : events) {
            if (ev.getId() == null || !ev.getId().startsWith("local-")) continue;
            
            int sales = LocalEventUtils.getSalesCount(ev.getId());
            String salesText = sales > 0 
                ? sales + " " + LocalizationManager.tr("discovery.card.sales")
                : LocalizationManager.tr("discovery.card.no_sales");
            
            String status = LocalEventUtils.getLocalEventStatusText(ev.getId(), false);
            boolean hasExport = sales > 0;
            
            // Format date using SwedishDateFormatter
            String dateText = LocalizationManager.tr("discovery.card.no_date");
            if (ev.getStartTime() != null) {
                dateText = SwedishDateFormatter.formatShortDate(ev.getStartTime().toZonedDateTime());
            }
            
            EventCard card = new EventCard(ev.getId(), ev.getName(), dateText, salesText, status, hasExport);
            eventCardsGrid.add(card);
        }
        
        expandedDetailsPanel.setVisible(false);
        eventCardsGrid.revalidate();
        eventCardsGrid.repaint();
    }

    @Override public void selectEventById(String eventId) {
        if (eventId == null || eventId.isBlank()) return;
        
        // Find card with matching eventId
        for (Component comp : eventCardsGrid.getComponents()) {
            if (comp instanceof EventCard card) {
                if (eventId.equals(card.eventId)) {
                    selectCard(card);
                    // Scroll to card
                    SwingUtilities.invokeLater(() -> {
                        card.scrollRectToVisible(card.getBounds());
                    });
                    return;
                }
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

        // Update provision bar visualization
        if (activeProvisionBar != null) {
            activeProvisionBar.setPercentages(
                Math.round(split.getMarketOwnerPercentage()),
                Math.round(split.getVendorPercentage()),
                Math.round(split.getPlatformProviderPercentage())
            );
        }

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
        deleteEventButton.setText(LocalizationManager.tr("discovery.button.delete"));

        noSelectionLabel.setText(LocalizationManager.tr("discovery.no_selection"));

        setEventTexts(discoveryDetailsHeaderLabel, discoveryRevenueSplitHeaderLabel,
                discoveryEventNameStaticLabel, discoveryEventDescStaticLabel,
                discoveryEventAddressStaticLabel, discoveryMarketOwnerStaticLabel,
                discoveryVendorStaticLabel, discoveryPlatformStaticLabel);

        selectedEventHeaderLabel.setText(LocalizationManager.tr("discovery.selected_event.title"));
        setEventTexts(detailsHeaderLabel, revenueSplitHeaderLabel,
                eventNameStaticLabel, eventDescStaticLabel, eventAddressStaticLabel,
                marketOwnerStaticLabel, vendorStaticLabel, platformStaticLabel);

        changeEventButton.setText(LocalizationManager.tr("button.change_event"));
        exportDataButton.setText(LocalizationManager.tr("export.button.export_data"));
        csvExportButton.setText(LocalizationManager.tr("export.button.export_csv"));

        if (AppModeManager.isLocalMode()) {
            activeEventNameLabel.setText(LocalizationManager.tr("event.local.name"));
            activeEventDescLabel.setText(LocalizationManager.tr("event.local.description"));
            activeEventAddressLabel.setText(
                    LocalizationManager.tr("event.no_street") + ", " + LocalizationManager.tr("event.no_city"));
            String eventId = AppModeManager.getEventId();
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

    /**
     * Inner class for visual provision bar displaying revenue split distribution.
     */
    private static class ProvisionBar extends JPanel {
        private int marketOwnerPercent = 10;
        private int vendorPercent = 85;
        private int platformPercent = 5;

        // Muted, harmonious palette
        private static final Color COLOR_MARKET_OWNER = AppColors.BORDER_MEDIUM; // Slate gray
        private static final Color COLOR_VENDOR = AppColors.ACCENT;               // Accent blue
        private static final Color COLOR_PLATFORM = AppColors.BORDER_LIGHT;       // Light gray

        public ProvisionBar() {
            setPreferredSize(new Dimension(200, 20));
            setOpaque(false);
        }

        public void setPercentages(int marketOwner, int vendor, int platform) {
            this.marketOwnerPercent = marketOwner;
            this.vendorPercent = vendor;
            this.platformPercent = platform;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int width = getWidth();
            int height = getHeight();
            int barHeight = Math.min(12, height);
            int y = (height - barHeight) / 2;
            int arc = barHeight;

            // Calculate widths
            int marketOwnerWidth = (int) (width * marketOwnerPercent / 100.0);
            int vendorWidth = (int) (width * vendorPercent / 100.0);
            int platformWidth = width - marketOwnerWidth - vendorWidth;

            // Draw full rounded background
            g2.setColor(COLOR_PLATFORM);
            g2.fillRoundRect(0, y, width, barHeight, arc, arc);

            // Draw vendor segment (clip to rounded rect)
            if (vendorWidth > 0) {
                g2.setColor(COLOR_VENDOR);
                g2.fillRect(marketOwnerWidth, y, vendorWidth, barHeight);
            }

            // Draw market owner segment (left, first)
            if (marketOwnerWidth > 0) {
                g2.setColor(COLOR_MARKET_OWNER);
                g2.fillRoundRect(0, y, marketOwnerWidth + arc / 2, barHeight, arc, arc);
                // Clean overlap
                g2.fillRect(marketOwnerWidth, y, 1, barHeight);
            }

            // Re-round the full bar edges
            Shape clip = g2.getClip();
            g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, y, width, barHeight, arc, arc));
            // Redraw all segments within rounded clip
            int x = 0;
            g2.setColor(COLOR_MARKET_OWNER);
            g2.fillRect(x, y, marketOwnerWidth, barHeight);
            x += marketOwnerWidth;
            g2.setColor(COLOR_VENDOR);
            g2.fillRect(x, y, vendorWidth, barHeight);
            x += vendorWidth;
            g2.setColor(COLOR_PLATFORM);
            g2.fillRect(x, y, platformWidth, barHeight);
            g2.setClip(clip);
        }
    }
}
