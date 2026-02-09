package se.goencoder.loppiskassan.controller;

import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.ui.dialogs.ExportDataDialog;

import java.awt.Frame;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.List;

/**
 * Controller for exporting local event data to JSONL files.
 * Provides functionality for slave cashiers to export their sales data
 * for sharing with a master cashier.
 */
public class ExportLocalEventController {

    /**
     * Export event data to a user-selected location.
     * 
     * @param eventId the local event ID to export
     * @param eventName the display name of the event
     */
    public static void exportEventData(String eventId, String eventName) {
        try {
            // 1. Read data to be exported
            List<V1SoldItem> items = JsonlHelper.readItems(
                LocalEventPaths.getPendingItemsPath(eventId)
            );
            
            if (items.isEmpty()) {
                Popup.WARNING.showAndWait(
                    LocalizationManager.tr("export.no_data.title"),
                    LocalizationManager.tr("export.no_data.message")
                );
                return;
            }
            
            // 2. Generate default filename
            String defaultFileName = generateFileName(eventName);
            
            // 3. Show export dialog
            File destination = showExportDialog(defaultFileName, items.size());
            if (destination == null) return; // User cancelled
            
            // 4. Copy pending_items.jsonl to destination
            Files.copy(
                LocalEventPaths.getPendingItemsPath(eventId),
                destination.toPath(),
                StandardCopyOption.REPLACE_EXISTING
            );
            
            // 5. Show success dialog
            showSuccessDialog(destination, items.size());
            
        } catch (Exception e) {
            Popup.ERROR.showAndWait(
                LocalizationManager.tr("export.error.title"),
                e.getMessage()
            );
        }
    }
    
    /**
     * Generate a sanitized filename for export.
     * Format: {eventname}-{date}.jsonl
     * 
     * @param eventName the display name of the event
     * @return sanitized filename
     */
    private static String generateFileName(String eventName) {
        if (eventName == null || eventName.isBlank()) {
            eventName = "local-event";
        }
        
        // "Sillfest Kassa 2" → "Sillfest-Kassa2"
        String sanitized = eventName
            .replaceAll("[^\\w\\s-]", "")  // Only alphanumeric + whitespace + hyphens
            .replaceAll("\\s+", "-")       // Whitespace → hyphen
            .toLowerCase();
        
        String date = LocalDate.now().toString(); // 2026-02-08
        return sanitized + "-" + date + ".jsonl";
    }
    
    /**
     * Show export dialog to user.
     * 
     * @param defaultFileName suggested filename
     * @param itemCount number of items to be exported
     * @return selected destination file, or null if cancelled
     */
    private static File showExportDialog(String defaultFileName, int itemCount) {
        // Try to find the main application frame as parent
        Frame parentFrame = null;
        for (Frame frame : Frame.getFrames()) {
            if (frame.isDisplayable() && frame.isVisible()) {
                parentFrame = frame;
                break;
            }
        }
        
        ExportDataDialog dialog = new ExportDataDialog(parentFrame, defaultFileName, itemCount);
        return dialog.showDialog();
    }
    
    /**
     * Show success dialog with file path and next steps.
     * 
     * @param destination the exported file
     * @param itemCount number of items exported
     */
    private static void showSuccessDialog(File destination, int itemCount) {
        String message = String.format(
            "%s\n\n📄 %s\n📁 %s\n\n%s",
            LocalizationManager.tr("export.success.file_saved"),
            destination.getName(),
            destination.getParent(),
            LocalizationManager.tr("export.success.next_steps")
        );
        
        Popup.INFORMATION.showAndWait(
            LocalizationManager.tr("export.success.title"),
            message
        );
    }
}