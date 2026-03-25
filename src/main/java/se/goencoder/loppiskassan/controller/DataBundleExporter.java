package se.goencoder.loppiskassan.controller;

import org.json.JSONObject;
import se.goencoder.loppiskassan.config.GlobalConfigurationStore;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.service.DialogService;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.V1SoldItem;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exports a complete data bundle (ZIP) from a local cashier.
 * <p>
 * The bundle contains all data files for the event in a standardized format,
 * making it easy to collect files from all cashiers after an event.
 * The filename includes the cashier nickname and a timestamp.
 */
public class DataBundleExporter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");
    private static final String DEFAULT_CASHIER_SLUG = "kassa";

    /**
     * Export a data bundle for the given event.
     * Prompts for cashier name if not set, then creates a ZIP with all event data.
     */
    public static void exportBundle(String eventId, String eventName) {
        // 1. Ensure cashier name is set
        String cashierName = ensureCashierName();
        if (cashierName == null) {
            return; // User cancelled
        }

        try {
            // 2. Read data
            Path pendingPath = LocalEventPaths.getPendingItemsPath(eventId);
            List<V1SoldItem> items = JsonlHelper.readItems(pendingPath);

            if (items.isEmpty()) {
                Popup.WARNING.showAndWait(
                        LocalizationManager.tr("export.no_data.title"),
                        LocalizationManager.tr("export.no_data.message")
                );
                return;
            }

            // 3. Generate filename and show save dialog
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String defaultFileName = buildDefaultFileName(eventId, cashierName, timestamp);

            Component parent = DialogService.getDialogParent();
            se.goencoder.loppiskassan.ui.dialogs.ExportDataDialog dialog =
                    new se.goencoder.loppiskassan.ui.dialogs.ExportDataDialog(
                            parent, defaultFileName, items.size(), ".zip");
            File destination = dialog.showDialog();
            if (destination == null) {
                return; // User cancelled
            }

            // 4. Create ZIP bundle
            createBundle(destination.toPath(), eventId, eventName, cashierName, items);

            // 5. Show success
            Popup.INFORMATION.showAndWait(
                    LocalizationManager.tr("bundle.success.title"),
                    LocalizationManager.tr("bundle.success.message",
                            destination.getName(),
                            destination.getParent(),
                            items.size(),
                            cashierName)
            );

        } catch (Exception e) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("export.error.title"),
                    e.getMessage()
            );
        }
    }

    /**
     * Ensure a cashier name is configured. Prompts the user if not set.
     *
     * @return the cashier name, or null if user cancelled
     */
    static String ensureCashierName() {
        String name = GlobalConfigurationStore.getCashierName();
        if (name != null && !name.isBlank()) {
            return name.trim();
        }

        // Prompt user for cashier name
        Component parent = DialogService.getDialogParent();
        String input = (String) JOptionPane.showInputDialog(
                parent,
                LocalizationManager.tr("bundle.cashier_name.prompt"),
                LocalizationManager.tr("bundle.cashier_name.title"),
                JOptionPane.QUESTION_MESSAGE,
                null,
                null,
                ""
        );

        if (input == null || input.isBlank()) {
            return null;
        }

        String trimmed = input.trim();
        GlobalConfigurationStore.setCashierName(trimmed);
        return trimmed;
    }

    /**
     * Create the ZIP bundle with all event data files.
     */
    static void createBundle(Path zipPath, String eventId, String eventName,
                             String cashierName, List<V1SoldItem> items) throws IOException {
        Path rejectedPath = LocalEventPaths.getRejectedPurchasesPath(eventId);
        createBundle(zipPath, eventId, eventName, cashierName, items, rejectedPath);
    }

    /**
     * Create the ZIP bundle with all event data files.
     * Accepts an explicit rejected-items path (useful for testing).
     */
    static void createBundle(Path zipPath, String eventId, String eventName,
                             String cashierName, List<V1SoldItem> items,
                             Path rejectedPath) throws IOException {
        Path parent = zipPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        try (ZipOutputStream zos = new ZipOutputStream(
                Files.newOutputStream(zipPath), StandardCharsets.UTF_8)) {

            // 1. Manifest with metadata
            JSONObject manifest = new JSONObject();
            manifest.put("cashierName", cashierName);
            manifest.put("eventId", eventId);
            manifest.put("eventName", eventName != null ? eventName : "");
            manifest.put("exportTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            manifest.put("itemCount", items.size());
            manifest.put("format", "loppiskassan-bundle-v1");

            int totalRevenue = items.stream().mapToInt(V1SoldItem::getPrice).sum();
            manifest.put("totalRevenue", totalRevenue);

            long uploadedCount = items.stream().filter(V1SoldItem::isUploaded).count();
            manifest.put("uploadedCount", uploadedCount);
            manifest.put("pendingCount", items.size() - uploadedCount);

            addTextEntry(zos, "manifest.json", manifest.toString(2));

            // 2. Pending items — stream JSONL directly to the zip entry.
            addPendingItemsEntry(zos, "pending_items.jsonl", items);

            // 3. Rejected items (if any)
            if (rejectedPath != null && Files.exists(rejectedPath) && Files.size(rejectedPath) > 0) {
                addFileEntry(zos, "rejected_purchases.jsonl", rejectedPath);
            }
        }
    }

    private static void addTextEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static void addFileEntry(ZipOutputStream zos, String name, Path file) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        Files.copy(file, zos);
        zos.closeEntry();
    }

    private static void addPendingItemsEntry(ZipOutputStream zos, String name, List<V1SoldItem> items) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        for (V1SoldItem item : items) {
            zos.write(JsonlHelper.toJsonLine(item).getBytes(StandardCharsets.UTF_8));
            zos.write('\n');
        }
        zos.closeEntry();
    }

    static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return DEFAULT_CASHIER_SLUG;
        }
        String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String slug = normalized
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9-]+", "")
                .replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? DEFAULT_CASHIER_SLUG : slug;
    }

    static String buildDefaultFileName(String eventId, String cashierName, String timestamp) {
        String sanitizedName = sanitize(cashierName);
        if (!DEFAULT_CASHIER_SLUG.equals(sanitizedName)) {
            return DEFAULT_CASHIER_SLUG + "-" + sanitizedName + "-" + timestamp + ".zip";
        }

        String eventSlug = sanitize(eventId);
        if (!DEFAULT_CASHIER_SLUG.equals(eventSlug)) {
            return DEFAULT_CASHIER_SLUG + "-" + eventSlug + "-" + timestamp + ".zip";
        }

        return DEFAULT_CASHIER_SLUG + "-" + timestamp + ".zip";
    }
}
