package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.storage.LocalEventPaths;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Stream;

/**
 * Arkiv-vy för lokal kassa.
 * Visar lista över arkiverade försäljningar (CSV-filer).
 */
public class ArchiveTabPanel extends JPanel implements LocalizationAware, SelectabableTab {
    
    private JLabel titleLabel;
    private JLabel descriptionLabel;
    private JTable archiveTable;
    private DefaultTableModel tableModel;
    private JButton openFileButton;
    private JButton refreshButton;
    
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm");
    
    public ArchiveTabPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColors.WHITE);
        
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.setBackground(AppColors.WHITE);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));
        
        // Header
        JPanel headerPanel = createHeaderPanel();
        contentPanel.add(headerPanel, BorderLayout.NORTH);
        
        // Table
        JPanel tablePanel = createTablePanel();
        contentPanel.add(tablePanel, BorderLayout.CENTER);
        
        // Buttons
        JPanel buttonPanel = createButtonPanel();
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        add(contentPanel, BorderLayout.CENTER);
        
        // Registrera för språkändringar
        LocalizationManager.addListener(this::reloadTexts);
        
        // Initialize texts on creation
        reloadTexts();
    }
    
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(AppColors.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
        
        titleLabel = new JLabel();
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(titleLabel);
        
        panel.add(Box.createVerticalStrut(8));
        
        descriptionLabel = new JLabel();
        descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(13f));
        descriptionLabel.setForeground(AppColors.TEXT_MUTED);
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(descriptionLabel);
        
        return panel;
    }
    
    private JPanel createTablePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(AppColors.WHITE);
        panel.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));
        
        // Tabell
        tableModel = new DefaultTableModel() {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        archiveTable = new JTable(tableModel);
        archiveTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        archiveTable.setRowHeight(32);
        archiveTable.setShowGrid(true);
        archiveTable.setGridColor(AppColors.BORDER);
        archiveTable.getTableHeader().setReorderingAllowed(false);
        archiveTable.setFont(archiveTable.getFont().deriveFont(13f));
        
        JScrollPane scrollPane = new JScrollPane(archiveTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(AppColors.BORDER, 1));
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createButtonPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        panel.setBackground(AppColors.WHITE);
        
        openFileButton = UserInterface.createButton("", 140, 36);
        openFileButton.addActionListener(e -> handleOpenFile());
        panel.add(openFileButton);
        
        refreshButton = UserInterface.createButton("", 140, 36);
        refreshButton.addActionListener(e -> loadArchiveFiles());
        panel.add(refreshButton);
        
        return panel;
    }
    
    private void loadArchiveFiles() {
        tableModel.setRowCount(0);
        
        try {
            // Get current event ID
            String eventId = AppModeManager.getEventId();
            if (eventId == null || eventId.isEmpty()) {
                // No event selected - show empty list
                openFileButton.setEnabled(false);
                return;
            }

            // Get event-specific archive directory
            Path archiveDir = LocalEventPaths.getArchiveDir(eventId);
            if (!Files.exists(archiveDir)) {
                // No archives yet - show empty list
                openFileButton.setEnabled(false);
                return;
            }
            
            List<ArchiveFileInfo> archives = new ArrayList<>();
            
            try (Stream<Path> paths = Files.list(archiveDir)) {
                paths.filter(path -> {
                    String name = path.getFileName().toString();
                    return name.startsWith(LocalizationManager.tr("history.archive_prefix")) 
                           && name.endsWith(".csv");
                })
                .forEach(path -> {
                    try {
                        BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                        long fileSize = attrs.size();
                        Date creationTime = new Date(attrs.creationTime().toMillis());
                        archives.add(new ArchiveFileInfo(
                            path.getFileName().toString(),
                            creationTime,
                            fileSize,
                            path.toAbsolutePath().toString()
                        ));
                    } catch (IOException e) {
                        // Skippa filer som inte kan läsas
                    }
                });
            }
            
            // Sortera efter datum (nyast först)
            archives.sort((a, b) -> b.creationTime.compareTo(a.creationTime));
            
            // Lägg till i tabell
            for (ArchiveFileInfo archive : archives) {
                tableModel.addRow(new Object[]{
                    archive.fileName,
                    dateFormat.format(archive.creationTime),
                    formatFileSize(archive.fileSize),
                    archive.fullPath
                });
            }
            
            openFileButton.setEnabled(archiveTable.getRowCount() > 0);
            
        } catch (IOException e) {
            Popup.ERROR.showAndWait(
                LocalizationManager.tr("error.load_archives"),
                e.getMessage());
        }
    }
    
    private void handleOpenFile() {
        int selectedRow = archiveTable.getSelectedRow();
        if (selectedRow >= 0) {
            String filePath = (String) tableModel.getValueAt(selectedRow, 3);
            try {
                Desktop.getDesktop().open(new File(filePath));
            } catch (IOException e) {
                Popup.ERROR.showAndWait(
                    LocalizationManager.tr("error.open_file"),
                    e.getMessage());
            }
        } else {
            Popup.INFORMATION.showAndWait(
                LocalizationManager.tr("archive.no_selection.title"),
                LocalizationManager.tr("archive.no_selection.message"));
        }
    }
    
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
    
    @Override
    public void selected() {
        loadArchiveFiles();
    }
    
    @Override
    public void reloadTexts() {
        titleLabel.setText(LocalizationManager.tr("archive.title"));
        descriptionLabel.setText(LocalizationManager.tr("archive.description"));
        openFileButton.setText(LocalizationManager.tr("archive.button.open"));
        refreshButton.setText(LocalizationManager.tr("archive.button.refresh"));
        
        // Uppdatera tabellkolumner
        tableModel.setColumnIdentifiers(new String[]{
            LocalizationManager.tr("archive.table.filename"),
            LocalizationManager.tr("archive.table.created"),
            LocalizationManager.tr("archive.table.size"),
            "Path" // Hidden column
        });
        
        // Dölj path-kolumnen
        if (archiveTable.getColumnCount() > 3) {
            archiveTable.removeColumn(archiveTable.getColumnModel().getColumn(3));
        }
        
        loadArchiveFiles();
    }
    
    @Override
    public void removeNotify() {
        LocalizationManager.removeListener(this::reloadTexts);
        super.removeNotify();
    }
    
    private static class ArchiveFileInfo {
        final String fileName;
        final Date creationTime;
        final long fileSize;
        final String fullPath;
        
        ArchiveFileInfo(String fileName, Date creationTime, long fileSize, String fullPath) {
            this.fileName = fileName;
            this.creationTime = creationTime;
            this.fileSize = fileSize;
            this.fullPath = fullPath;
        }
    }
}
