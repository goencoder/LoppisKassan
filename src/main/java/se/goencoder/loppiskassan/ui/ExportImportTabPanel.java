package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.controller.CsvExportController;
import se.goencoder.loppiskassan.controller.ExportLocalEventController;
import se.goencoder.loppiskassan.controller.HistoryTabController;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.storage.LocalEventRepository;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.utils.LocalEventUtils;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * Export/Import-vy för lokal kassa.
 * Hanterar export av evenemangdata till JSONL/CSV och import av externa JSONL-filer.
 */
public class ExportImportTabPanel extends JPanel implements LocalizationAware, SelectabableTab {
    
    private JLabel titleLabel;
    private JLabel descriptionLabel;
    private JLabel eventNameLabel;
    private JLabel eventNameValue;
    private JLabel salesCountLabel;
    private JLabel salesCountValue;
    
    private JButton exportJsonlButton;
    private JButton exportCsvButton;
    private JButton importButton;
    
    private JPanel statsPanel;
    
    public ExportImportTabPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColors.WHITE);
        
        // Huvudinnehåll
        JPanel contentPanel = new JPanel(new GridBagLayout());
        contentPanel.setBackground(AppColors.WHITE);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));
        
        JPanel card = createCard();
        contentPanel.add(card);
        
        add(contentPanel, BorderLayout.CENTER);
        
        // Registrera för språkändringar
        LocalizationManager.addListener(this::reloadTexts);
    }
    
    private JPanel createCard() {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AppColors.WHITE);
        card.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));
        card.setMaximumSize(new Dimension(600, Integer.MAX_VALUE));
        
        // Titel
        titleLabel = new JLabel();
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(titleLabel);
        
        card.add(Box.createVerticalStrut(8));
        
        // Beskrivning
        descriptionLabel = new JLabel();
        descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(13f));
        descriptionLabel.setForeground(AppColors.TEXT_MUTED);
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(descriptionLabel);
        
        card.add(Box.createVerticalStrut(24));
        
        // Evenemangs-info
        statsPanel = createStatsPanel();
        statsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(statsPanel);
        
        card.add(Box.createVerticalStrut(24));
        
        // Export-sektion
        JPanel exportSection = createExportSection();
        exportSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(exportSection);
        
        card.add(Box.createVerticalStrut(24));
        
        // Import-sektion
        JPanel importSection = createImportSection();
        importSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(importSection);
        
        return card;
    }
    
    private JPanel createStatsPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 2, 16, 8));
        panel.setBackground(AppColors.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, AppColors.BORDER),
            BorderFactory.createEmptyBorder(16, 16, 16, 16)
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 100));
        
        // Evenemangsnamn
        eventNameLabel = new JLabel();
        eventNameLabel.setFont(eventNameLabel.getFont().deriveFont(Font.BOLD, 12f));
        eventNameLabel.setForeground(AppColors.TEXT_MUTED);
        panel.add(eventNameLabel);
        
        eventNameValue = new JLabel();
        eventNameValue.setFont(eventNameValue.getFont().deriveFont(Font.PLAIN, 12f));
        panel.add(eventNameValue);
        
        // Antal försäljningar
        salesCountLabel = new JLabel();
        salesCountLabel.setFont(salesCountLabel.getFont().deriveFont(Font.BOLD, 12f));
        salesCountLabel.setForeground(AppColors.TEXT_MUTED);
        panel.add(salesCountLabel);
        
        salesCountValue = new JLabel();
        salesCountValue.setFont(salesCountValue.getFont().deriveFont(Font.PLAIN, 12f));
        panel.add(salesCountValue);
        
        return panel;
    }
    
    private JPanel createExportSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(AppColors.WHITE);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        
        JLabel sectionTitle = new JLabel();
        sectionTitle.setText(LocalizationManager.tr("export.section.title"));
        sectionTitle.setFont(sectionTitle.getFont().deriveFont(Font.BOLD, 14f));
        sectionTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(sectionTitle);
        
        section.add(Box.createVerticalStrut(8));
        
        JLabel sectionDesc = new JLabel();
        sectionDesc.setText(LocalizationManager.tr("export.section.description"));
        sectionDesc.setFont(sectionDesc.getFont().deriveFont(Font.PLAIN, 12f));
        sectionDesc.setForeground(AppColors.TEXT_MUTED);
        sectionDesc.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(sectionDesc);
        
        section.add(Box.createVerticalStrut(12));
        
        // Export-knappar
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonsPanel.setBackground(AppColors.WHITE);
        buttonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        exportJsonlButton = UserInterface.createButton("", 160, 36);
        AppButton.applyStyle(exportJsonlButton, AppButton.Variant.SECONDARY, AppButton.Size.MEDIUM);
        exportJsonlButton.addActionListener(e -> handleExportJsonl());
        buttonsPanel.add(exportJsonlButton);
        
        exportCsvButton = UserInterface.createButton("", 160, 36);
        AppButton.applyStyle(exportCsvButton, AppButton.Variant.SECONDARY, AppButton.Size.MEDIUM);
        exportCsvButton.addActionListener(e -> handleExportCsv());
        buttonsPanel.add(exportCsvButton);
        
        section.add(buttonsPanel);
        
        return section;
    }
    
    private JPanel createImportSection() {
        JPanel section = new JPanel();
        section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));
        section.setBackground(AppColors.WHITE);
        section.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        
        JLabel sectionTitle = new JLabel();
        sectionTitle.setText(LocalizationManager.tr("import.section.title"));
        sectionTitle.setFont(sectionTitle.getFont().deriveFont(Font.BOLD, 14f));
        sectionTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(sectionTitle);
        
        section.add(Box.createVerticalStrut(8));
        
        JLabel sectionDesc = new JLabel();
        sectionDesc.setText(LocalizationManager.tr("import.section.description"));
        sectionDesc.setFont(sectionDesc.getFont().deriveFont(Font.PLAIN, 12f));
        sectionDesc.setForeground(AppColors.TEXT_MUTED);
        sectionDesc.setAlignmentX(Component.LEFT_ALIGNMENT);
        section.add(sectionDesc);
        
        section.add(Box.createVerticalStrut(12));
        
        // Import-knapp
        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonsPanel.setBackground(AppColors.WHITE);
        buttonsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        importButton = UserInterface.createButton("", 160, 36);
        AppButton.applyStyle(importButton, AppButton.Variant.SECONDARY, AppButton.Size.MEDIUM);
        importButton.addActionListener(e -> handleImport());
        buttonsPanel.add(importButton);
        
        section.add(buttonsPanel);
        
        return section;
    }
    
    private void handleExportJsonl() {
        String eventId = AppModeManager.getEventId();
        if (eventId == null) {
            Popup.ERROR.showAndWait(
                LocalizationManager.tr("error.no_event_selected.title"),
                LocalizationManager.tr("error.no_event_selected.message"));
            return;
        }
        
        ExportLocalEventController.exportEventData(eventId, eventNameValue.getText());
        updateStats();
    }
    
    private void handleExportCsv() {
        String eventId = AppModeManager.getEventId();
        if (eventId == null) {
            Popup.ERROR.showAndWait(
                LocalizationManager.tr("error.no_event_selected.title"),
                LocalizationManager.tr("error.no_event_selected.message"));
            return;
        }
        
        CsvExportController.exportEventDataAsCsv(eventId, eventNameValue.getText());
        updateStats();
    }
    
    private void handleImport() {
        String eventId = AppModeManager.getEventId();
        if (eventId == null) {
            Popup.ERROR.showAndWait(
                LocalizationManager.tr("error.no_event_selected.title"),
                LocalizationManager.tr("error.no_event_selected.message"));
            return;
        }
        
        // Använd befintlig import-funktionalitet från HistoryTabController
        HistoryTabController controller = HistoryTabController.getInstance();
        controller.buttonAction(Constants.BUTTON_IMPORT);
        
        // Uppdatera statistik efter import
        updateStats();
    }
    
    private void updateStats() {
        String eventId = AppModeManager.getEventId();
        if (eventId == null) {
            eventNameValue.setText("-");
            salesCountValue.setText("0");
            exportJsonlButton.setEnabled(false);
            exportCsvButton.setEnabled(false);
            importButton.setEnabled(false);
            return;
        }
        
        try {
            var event = LocalEventRepository.load(eventId);
            eventNameValue.setText(event.getName());
            
            int salesCount = LocalEventUtils.getSalesCount(eventId);
            salesCountValue.setText(String.valueOf(salesCount));
            
            boolean hasSales = salesCount > 0;
            exportJsonlButton.setEnabled(hasSales);
            exportCsvButton.setEnabled(hasSales);
            importButton.setEnabled(true);
        } catch (IOException e) {
            eventNameValue.setText("-");
            salesCountValue.setText("0");
            exportJsonlButton.setEnabled(false);
            exportCsvButton.setEnabled(false);
            importButton.setEnabled(false);
        }
    }
    
    @Override
    public void selected() {
        updateStats();
    }
    
    public Component getComponent() {
        return this;
    }
    
    @Override
    public void reloadTexts() {
        titleLabel.setText(LocalizationManager.tr("export_import.title"));
        descriptionLabel.setText(LocalizationManager.tr("export_import.description"));
        eventNameLabel.setText(LocalizationManager.tr("export_import.event_name"));
        salesCountLabel.setText(LocalizationManager.tr("export_import.sales_count"));
        exportJsonlButton.setText(LocalizationManager.tr("export.button.jsonl"));
        exportCsvButton.setText(LocalizationManager.tr("export.button.csv"));
        importButton.setText(LocalizationManager.tr("import.button"));
        
        updateStats();
    }
    
    @Override
    public void removeNotify() {
        LocalizationManager.removeListener(this::reloadTexts);
        super.removeNotify();
    }
}
