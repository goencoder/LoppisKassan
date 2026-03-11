package se.goencoder.loppiskassan.controller;

import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.service.DialogService;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.ui.dialogs.ExportDataDialog;

import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Controller for exporting local event data to CSV files.
 * <p>
 * This export is intended for opening in spreadsheet applications (Excel, Google Sheets, etc.)
 * and is NOT for importing into other LoppisKassan instances (use JSONL export for that).
 * </p>
 */
public class CsvExportController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String CSV_SEPARATOR = ";";

    /**
     * Export event data as CSV to a user-selected location.
     *
     * @param eventId   the local event ID to export
     * @param eventName the display name of the event
     */
    public static void exportEventDataAsCsv(String eventId, String eventName) {
        try {
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

            String defaultFileName = generateCsvFileName(eventName);

            File destination = showExportDialog(defaultFileName, items.size());
            if (destination == null) return;

            writeCsv(destination, items);

            showSuccessDialog(destination, items.size());

        } catch (Exception e) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("export.error.title"),
                    e.getMessage()
            );
        }
    }

    /**
     * Generate a sanitized CSV filename.
     * Format: {eventname}-{date}.csv
     */
    private static String generateCsvFileName(String eventName) {
        if (eventName == null || eventName.isBlank()) {
            eventName = "local-event";
        }
        String sanitized = eventName
                .replaceAll("[^\\w\\s-]", "")
                .replaceAll("\\s+", "-")
                .toLowerCase();
        String date = LocalDate.now().toString();
        return sanitized + "-" + date + ".csv";
    }

    private static File showExportDialog(String defaultFileName, int itemCount) {
        Component parentComponent = DialogService.getDialogParent();
        ExportDataDialog dialog = new ExportDataDialog(parentComponent, defaultFileName, itemCount, ".csv");
        return dialog.showDialog();
    }

    /**
     * Write sold items to a CSV file with semicolon separator (Excel-friendly).
     */
    private static void writeCsv(File destination, List<V1SoldItem> items) throws IOException {
        try (PrintWriter pw = new PrintWriter(destination, StandardCharsets.UTF_8)) {
            // BOM for Excel UTF-8 detection
            pw.print('\uFEFF');

            // Header
            pw.println(String.join(CSV_SEPARATOR,
                    LocalizationManager.tr("csv.header.seller"),
                    LocalizationManager.tr("csv.header.price"),
                    LocalizationManager.tr("csv.header.payment_method"),
                    LocalizationManager.tr("csv.header.sold_time"),
                    LocalizationManager.tr("csv.header.purchase_id"),
                    LocalizationManager.tr("csv.header.item_id")
            ));

            // Rows
            for (V1SoldItem item : items) {
                String time = item.getSoldTime() != null
                        ? item.getSoldTime().format(TIME_FORMAT) : "";
                String method = item.getPaymentMethod() != null
                        ? item.getPaymentMethod().name() : "";

                pw.println(String.join(CSV_SEPARATOR,
                        String.valueOf(item.getSeller()),
                        String.valueOf(item.getPrice()),
                        method,
                        time,
                        safe(item.getPurchaseId()),
                        safe(item.getItemId())
                ));
            }
        }
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    private static void showSuccessDialog(File destination, int itemCount) {
        String message = String.format(
                "%s\n\n📄 %s\n📁 %s\n\n%s",
                LocalizationManager.tr("export.csv.success.file_saved"),
                destination.getName(),
                destination.getParent(),
                LocalizationManager.tr("export.csv.success.tip")
        );

        Popup.INFORMATION.showAndWait(
                LocalizationManager.tr("export.csv.success.title"),
                message
        );
    }
}
