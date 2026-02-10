package se.goencoder.loppiskassan.ui.dialogs;

import se.goencoder.iloppis.model.V1Event;
import se.goencoder.iloppis.model.V1EventFilter;
import se.goencoder.iloppis.model.V1FilterEventsRequest;
import se.goencoder.iloppis.model.V1FilterEventsResponse;
import se.goencoder.iloppis.model.V1Pagination;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.controller.BulkUploadController;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.model.BulkUploadResult;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.storage.LocalEvent;
import se.goencoder.loppiskassan.storage.PendingItemsStore;
import se.goencoder.loppiskassan.ui.AppButton;
import se.goencoder.loppiskassan.ui.AppColors;
import se.goencoder.loppiskassan.ui.ProgressDialog;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dialog for uploading local event data to the iLoppis backend.
 * User searches for backend event, enters code, reviews preview, and initiates upload.
 */
public class BulkUploadDialog extends JDialog {
    private final LocalEvent localEvent;
    
    private JTextField searchField;
    private JComboBox<V1Event> backendEventCombo;
    private JTextField codeField;
    private JLabel previewLabel;
    private JButton uploadButton;
    private JButton cancelButton;
    
    private BulkUploadResult uploadResult;
    private List<V1Event> allEvents = new ArrayList<>();
    
    public BulkUploadDialog(Frame owner, LocalEvent localEvent) {
        super(owner, LocalizationManager.tr("bulk_upload.title"), ModalityType.APPLICATION_MODAL);
        this.localEvent = localEvent;
        this.uploadResult = null;
        
        initializeComponents();
        layoutComponents();
        setupEventHandlers();
        loadBackendEvents();
        updatePreview();
        
        pack();
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    }
    
    /**
     * Show dialog and return upload result, or null if cancelled
     */
    public BulkUploadResult showDialog() {
        setVisible(true);
        return uploadResult;
    }
    
    private void initializeComponents() {
        searchField = new JTextField(20);
        searchField.setToolTipText("Search events...");
        
        backendEventCombo = new JComboBox<>();
        backendEventCombo.setPreferredSize(new Dimension(300, 30));
        
        // Custom renderer to show event names instead of object toString()
        backendEventCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, 
                    int index, boolean isSelected, boolean cellHasFocus) {
                if (value instanceof V1Event) {
                    V1Event event = (V1Event) value;
                    String displayText = event.getName() + " • " + event.getAddressCity();
                    return super.getListCellRendererComponent(list, displayText, index, isSelected, cellHasFocus);
                }
                return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            }
        });
        
        codeField = new JTextField(20);
        codeField.setToolTipText("XXX-XXX");
        
        previewLabel = new JLabel("");
        previewLabel.setForeground(AppColors.PREVIEW_TEXT);
        previewLabel.setFont(new Font("Monospaced", Font.PLAIN, 12));
        
        uploadButton = AppButton.create(LocalizationManager.tr("bulk_upload.upload"), AppButton.Variant.PRIMARY, AppButton.Size.MEDIUM);
        
        cancelButton = AppButton.create(LocalizationManager.tr("bulk_upload.cancel"), AppButton.Variant.SECONDARY, AppButton.Size.MEDIUM);
    }
    
    private void layoutComponents() {
        setLayout(new BorderLayout(10, 10));
        
        // Main content
        JPanel contentPanel = new JPanel();
        contentPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        
        // Title
        JLabel titleLabel = new JLabel(localEvent.getName());
        titleLabel.setFont(new Font("Arial", Font.BOLD, 16));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(titleLabel);
        contentPanel.add(Box.createVerticalStrut(15));
        
        // Search field for events
        JLabel searchLabel = new JLabel("Sök backend-event / Search event:");
        searchLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(searchLabel);
        contentPanel.add(Box.createVerticalStrut(5));
        
        searchField.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        contentPanel.add(searchField);
        contentPanel.add(Box.createVerticalStrut(10));
        
        // Backend event selection
        JLabel eventLabel = new JLabel(LocalizationManager.tr("bulk_upload.backend_event"));
        eventLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(eventLabel);
        contentPanel.add(Box.createVerticalStrut(5));
        
        backendEventCombo.setAlignmentX(Component.LEFT_ALIGNMENT);
        backendEventCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        contentPanel.add(backendEventCombo);
        contentPanel.add(Box.createVerticalStrut(15));
        
        // Code field
        JLabel codeLabel = new JLabel(LocalizationManager.tr("bulk_upload.code"));
        codeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(codeLabel);
        contentPanel.add(Box.createVerticalStrut(5));
        
        codeField.setAlignmentX(Component.LEFT_ALIGNMENT);
        codeField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        contentPanel.add(codeField);
        contentPanel.add(Box.createVerticalStrut(15));
        
        // Preview
        JLabel previewTitleLabel = new JLabel(LocalizationManager.tr("bulk_upload.preview"));
        previewTitleLabel.setFont(new Font("Arial", Font.BOLD, 12));
        previewTitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(previewTitleLabel);
        contentPanel.add(Box.createVerticalStrut(5));
        
        JPanel previewBoxPanel = new JPanel(new BorderLayout());
        previewBoxPanel.setBorder(BorderFactory.createLineBorder(AppColors.PREVIEW_BORDER));
        previewBoxPanel.setBackground(AppColors.PREVIEW_BG);
        previewBoxPanel.add(previewLabel, BorderLayout.CENTER);
        previewLabel.setBorder(new EmptyBorder(10, 10, 10, 10));
        previewBoxPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        previewBoxPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        contentPanel.add(previewBoxPanel);
        
        add(contentPanel, BorderLayout.CENTER);
        
        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        buttonPanel.setBorder(new EmptyBorder(10, 20, 20, 20));
        buttonPanel.add(cancelButton);
        buttonPanel.add(uploadButton);
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    private void setupEventHandlers() {
        cancelButton.addActionListener(e -> dispose());
        
        uploadButton.addActionListener(e -> performUpload());
        
        // Search field listener - filter events as user types
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterEvents(); }
            public void removeUpdate(DocumentEvent e) { filterEvents(); }
            public void changedUpdate(DocumentEvent e) { filterEvents(); }
        });
        
        codeField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateUploadButtonState(); }
            public void removeUpdate(DocumentEvent e) { updateUploadButtonState(); }
            public void changedUpdate(DocumentEvent e) { updateUploadButtonState(); }
        });
        
        backendEventCombo.addActionListener(e -> updateUploadButtonState());
        
        updateUploadButtonState();
    }
    
    private void filterEvents() {
        String searchText = searchField.getText().trim();
        backendEventCombo.removeAllItems();
        
        for (V1Event event : allEvents) {
            String name = event.getName().toLowerCase();
            String city = event.getAddressCity().toLowerCase();
            if (searchText.isEmpty() || name.contains(searchText.toLowerCase()) || 
                city.contains(searchText.toLowerCase())) {
                backendEventCombo.addItem(event);
            }
        }
    }
    
    private void loadBackendEvents() {
        new SwingWorker<List<V1Event>, Void>() {
            @Override
            protected List<V1Event> doInBackground() {
                try {
                    V1EventFilter eventFilter = new V1EventFilter();
                    eventFilter.setDateFrom(OffsetDateTime.now().minusDays(30));

                    V1Pagination pagination = new V1Pagination();
                    pagination.setPageSize(100);

                    V1FilterEventsRequest request = new V1FilterEventsRequest();
                    request.setFilter(eventFilter);
                    request.setPagination(pagination);

                    V1FilterEventsResponse response = ApiHelper.INSTANCE
                        .getEventServiceApi()
                        .eventServiceFilterEvents(request);
                    
                    return response != null && response.getEvents() != null 
                        ? response.getEvents() 
                        : new ArrayList<>();
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(
                        BulkUploadDialog.this,
                        LocalizationManager.tr("bulk_upload.error_loading_events") + ": " + e.getMessage(),
                        LocalizationManager.tr("error"),
                        JOptionPane.ERROR_MESSAGE
                    );
                    return new ArrayList<>();
                }
            }
            
            @Override
            protected void done() {
                try {
                    allEvents = get();
                    filterEvents(); // Show all events initially
                } catch (Exception ignored) {
                }
            }
        }.execute();
    }
    
    private void updatePreview() {
        try {
            PendingItemsStore store = new PendingItemsStore(localEvent.getEventId());
            List<V1SoldItem> items = store.readAll();
            
            long purchaseCount = items.stream()
                .map(V1SoldItem::getPurchaseId)
                .distinct()
                .count();
            
            int totalPrice = items.stream()
                .mapToInt(V1SoldItem::getPrice)
                .sum();
            
            String previewText = String.format(
                "%d items\n%d purchases\n%d SEK",
                items.size(),
                purchaseCount,
                totalPrice
            );
            previewLabel.setText(previewText);
        } catch (IOException e) {
            previewLabel.setText("Error loading items: " + e.getMessage());
        }
    }
    
    private void performUpload() {
        V1Event selectedEvent = (V1Event) backendEventCombo.getSelectedItem();
        if (selectedEvent == null) {
            JOptionPane.showMessageDialog(
                this,
                LocalizationManager.tr("bulk_upload.select_event"),
                LocalizationManager.tr("error"),
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        
        String code = codeField.getText().trim();
        if (code.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                LocalizationManager.tr("bulk_upload.enter_code"),
                LocalizationManager.tr("error"),
                JOptionPane.WARNING_MESSAGE
            );
            codeField.requestFocus();
            return;
        }

        // Use ProgressDialog.runTask for background upload
        ProgressDialog.runTask(
            this,
            LocalizationManager.tr("bulk_upload.title"),
            LocalizationManager.tr("bulk_upload.uploading"),
            () -> BulkUploadController.uploadLocalEventData(
                localEvent,
                selectedEvent,
                code
            ),
            result -> {
                uploadResult = result;
                showUploadSummary(result);
                dispose();
            },
            error -> {
                String fullMessage = error.getMessage();
                String userMessage = fullMessage;
                
                // Parse error to provide better context
                if (fullMessage.contains("401")) {
                    userMessage = "Kassakoden är felaktig eller utgången. Vänligen kontrollera koden.\n\n(Code validation failed)";
                } else if (fullMessage.contains("403")) {
                    userMessage = "Du har inte behörighet att ladda upp till detta evenemang.\n\n(Permission denied)";
                } else if (fullMessage.contains("404")) {
                    userMessage = "Eventet hittades inte. Försök välja ett annat evenemang.\n\n(Event not found)";
                } else if (fullMessage.contains("Network") || fullMessage.contains("connection")) {
                    userMessage = "Nätverksfel. Kontrollera din internetanslutning.\n\n(Network error)";
                }
                
                JOptionPane.showMessageDialog(
                    BulkUploadDialog.this,
                    userMessage,
                    LocalizationManager.tr("error"),
                    JOptionPane.ERROR_MESSAGE
                );
            }
        );
    }
    
    private void showUploadSummary(BulkUploadResult result) {
        String message = buildSummaryMessage(result);
        String title = result.isFullSuccess() 
            ? LocalizationManager.tr("bulk_upload.success")
            : LocalizationManager.tr("bulk_upload.summary");
        
        int messageType = result.isFullSuccess() 
            ? JOptionPane.INFORMATION_MESSAGE 
            : JOptionPane.WARNING_MESSAGE;
        
        JOptionPane.showMessageDialog(
            this,
            message,
            title,
            messageType
        );
    }
    
    private String buildSummaryMessage(BulkUploadResult result) {
        StringBuilder sb = new StringBuilder();
        
        if (result.isFullSuccess()) {
            sb.append(String.format("✅ %d items uploaded successfully", result.acceptedItems.size()));
        } else if (result.isPartialSuccess()) {
            sb.append(String.format("✅ %d items accepted\n", result.acceptedItems.size()));
            if (!result.duplicateItems.isEmpty()) {
                sb.append(String.format("⚠️ %d items already uploaded (duplicates)\n", result.duplicateItems.size()));
            }
            if (!result.failedItems.isEmpty()) {
                sb.append(String.format("❌ %d items failed\n", result.failedItems.size()));
            }
        } else if (!result.errorMessages.isEmpty()) {
            sb.append("❌ Upload failed:\n");
            for (String error : result.errorMessages) {
                sb.append("  • ").append(error).append("\n");
            }
        } else {
            sb.append("No items to upload");
        }
        
        return sb.toString();
    }
    
    private void updateUploadButtonState() {
        boolean canUpload = backendEventCombo.getSelectedItem() != null 
            && !codeField.getText().trim().isEmpty();
        uploadButton.setEnabled(canUpload);
    }
}
