package se.goencoder.loppiskassan.ui.dialogs;

import se.goencoder.loppiskassan.localization.LocalizationManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * Dialog for exporting local event data to JSONL files.
 * Provides user-friendly interface for selecting destination and filename.
 */
public class ExportDataDialog extends JDialog {
    
    private JTextField destinationField;
    private JTextField filenameField;
    private JButton browseButton;
    private JButton exportButton;
    private JButton cancelButton;
    
    private File selectedDestination;
    private final String defaultFilename;
    private final int itemCount;
    private final String fileExtension;
    private boolean confirmed = false;
    
    public ExportDataDialog(Component parent, String defaultFilename, int itemCount) {
        this(parent, defaultFilename, itemCount, ".jsonl");
    }

    public ExportDataDialog(Component parent, String defaultFilename, int itemCount, String fileExtension) {
        super(parent != null ? SwingUtilities.getWindowAncestor(parent) : null, 
              LocalizationManager.tr("export.dialog.title"), 
              ModalityType.APPLICATION_MODAL);
        
        this.defaultFilename = defaultFilename;
        this.itemCount = itemCount;
        this.fileExtension = fileExtension;
        
        initializeComponents();
        layoutComponents();
        setupEventHandlers();
        setDefaultValues();
        
        pack();
        setLocationRelativeTo(parent);
        setResizable(false);
    }
    
    /**
     * Show the dialog and return the selected file, or null if cancelled.
     */
    public File showDialog() {
        setVisible(true);
        return confirmed ? getSelectedFile() : null;
    }
    
    private void initializeComponents() {
        destinationField = new JTextField(30);
        destinationField.setEditable(false);
        
        filenameField = new JTextField(30);
        
        browseButton = new JButton("📂");
        browseButton.setToolTipText(LocalizationManager.tr("export.dialog.browse"));
        
        exportButton = new JButton(LocalizationManager.tr("export.dialog.export"));
        cancelButton = new JButton(LocalizationManager.tr("export.dialog.cancel"));
    }
    
    private void layoutComponents() {
        setLayout(new BorderLayout(10, 10));
        
        // Main content panel
        JPanel mainPanel = new JPanel();
        mainPanel.setBorder(new EmptyBorder(20, 20, 10, 20));
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        
        // Event info
        JLabel eventInfoLabel = new JLabel(String.format(
            "<html><b>%s</b><br/>%s: %d %s</html>",
            LocalizationManager.tr("export.dialog.event_info"),
            LocalizationManager.tr("export.dialog.sales_count"),
            itemCount,
            LocalizationManager.tr("export.dialog.items")
        ));
        eventInfoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(eventInfoLabel);
        mainPanel.add(Box.createVerticalStrut(15));
        
        // Destination row
        JLabel saveToLabel = new JLabel(LocalizationManager.tr("export.dialog.save_to"));
        saveToLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(saveToLabel);
        mainPanel.add(Box.createVerticalStrut(5));
        
        JPanel destinationPanel = new JPanel(new BorderLayout(5, 0));
        destinationPanel.add(destinationField, BorderLayout.CENTER);
        destinationPanel.add(browseButton, BorderLayout.EAST);
        destinationPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(destinationPanel);
        mainPanel.add(Box.createVerticalStrut(10));
        
        // Filename row
        JLabel filenameLabel = new JLabel(LocalizationManager.tr("export.dialog.filename"));
        filenameLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(filenameLabel);
        mainPanel.add(Box.createVerticalStrut(5));
        
        filenameField.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(filenameField);
        mainPanel.add(Box.createVerticalStrut(15));
        
        // Tip
        JLabel tipLabel = new JLabel(String.format(
            "<html><div style='color: #666; font-size: 11px;'>💡 %s</div></html>",
            LocalizationManager.tr("export.dialog.tip")
        ));
        tipLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        mainPanel.add(tipLabel);
        
        add(mainPanel, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBorder(new EmptyBorder(10, 20, 20, 20));
        buttonPanel.add(cancelButton);
        buttonPanel.add(exportButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    private void setupEventHandlers() {
        browseButton.addActionListener(this::browseForDestination);
        exportButton.addActionListener(this::confirmExport);
        cancelButton.addActionListener(e -> dispose());
        
        // Allow Enter key in filename field to confirm
        filenameField.addActionListener(this::confirmExport);
        
        // Update export button state when filename changes
        filenameField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { updateExportButtonState(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { updateExportButtonState(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { updateExportButtonState(); }
        });
    }
    
    private void setDefaultValues() {
        // Default to Desktop
        String userHome = System.getProperty("user.home");
        File desktop = new File(userHome, "Desktop");
        if (desktop.exists() && desktop.isDirectory()) {
            selectedDestination = desktop;
        } else {
            selectedDestination = new File(userHome);
        }
        
        destinationField.setText(selectedDestination.getAbsolutePath());
        filenameField.setText(defaultFilename);
        
        updateExportButtonState();
    }
    
    private void browseForDestination(ActionEvent e) {
        JFileChooser chooser = new JFileChooser(selectedDestination);
        chooser.setDialogTitle(LocalizationManager.tr("export.dialog.select_folder"));
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            selectedDestination = chooser.getSelectedFile();
            destinationField.setText(selectedDestination.getAbsolutePath());
            updateExportButtonState();
        }
    }
    
    private void confirmExport(ActionEvent e) {
        String filename = filenameField.getText().trim();
        if (filename.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                LocalizationManager.tr("export.dialog.filename_required"),
                LocalizationManager.tr("export.dialog.validation_error"),
                JOptionPane.WARNING_MESSAGE
            );
            filenameField.requestFocus();
            return;
        }
        
        // Add file extension if not present
        if (!filename.toLowerCase().endsWith(fileExtension)) {
            filename += fileExtension;
            filenameField.setText(filename);
        }
        
        File targetFile = new File(selectedDestination, filename);
        if (targetFile.exists()) {
            int choice = JOptionPane.showConfirmDialog(
                this,
                LocalizationManager.tr("export.dialog.file_exists"),
                LocalizationManager.tr("export.dialog.confirm_overwrite"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
            );
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        }
        
        confirmed = true;
        dispose();
    }
    
    private void updateExportButtonState() {
        boolean canExport = selectedDestination != null 
                         && selectedDestination.exists() 
                         && selectedDestination.canWrite()
                         && !filenameField.getText().trim().isEmpty();
        exportButton.setEnabled(canExport);
    }
    
    private File getSelectedFile() {
        String filename = filenameField.getText().trim();
        if (!filename.toLowerCase().endsWith(fileExtension)) {
            filename += fileExtension;
        }
        return new File(selectedDestination, filename);
    }
}