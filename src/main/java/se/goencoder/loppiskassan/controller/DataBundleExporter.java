package se.goencoder.loppiskassan.controller;

import org.json.JSONObject;
import se.goencoder.loppiskassan.config.GlobalConfigurationStore;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.service.DialogService;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.ui.dialogs.ExportDataDialog;
import se.goencoder.loppiskassan.util.AppPaths;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates a ZIP bundle with troubleshooting data for iLoppis support.
 */
public class DataBundleExporter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");
    private static final String DEFAULT_CASHIER_SLUG = "kassa";

    /**
     * Export a support bundle for the selected iLoppis event.
     */
    public static void exportBundle(String eventId, String eventName) {
        String cashierName = ensureCashierName();
        if (cashierName == null || eventId == null || eventId.isBlank()) {
            return;
        }

        try {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String defaultFileName = "iloppis-support-" + buildDefaultFileName(eventId, cashierName, timestamp);

            Component parent = DialogService.getDialogParent();
            ExportDataDialog dialog = new ExportDataDialog(
                    parent,
                    defaultFileName,
                    getBundleEntryCount(eventId),
                    ".zip",
                    "support_bundle.dialog.title",
                    "support_bundle.dialog.tip");
            File destination = dialog.showDialog();
            if (destination == null) {
                return;
            }

            createBundle(destination.toPath(), eventId, eventName, cashierName);

            Popup.INFORMATION.showAndWait(
                    LocalizationManager.tr("support_bundle.success.title"),
                    LocalizationManager.tr("support_bundle.success.message",
                            destination.getName(),
                            destination.getParent(),
                            cashierName)
            );
        } catch (Exception e) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("support_bundle.error.title"),
                    e.getMessage()
            );
        }
    }

    static String ensureCashierName() {
        String name = GlobalConfigurationStore.getCashierName();
        if (name != null && !name.isBlank()) {
            return name.trim();
        }

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

    static void createBundle(Path zipPath, String eventId, String eventName,
                             String cashierName) throws IOException {
        createBundle(zipPath, eventId, eventName, cashierName,
                LocalEventPaths.getEventDir(eventId),
                AppPaths.getConfigDir(),
                AppPaths.getLogsDir());
    }

    static void createBundle(Path zipPath, String eventId, String eventName,
                             String cashierName, Path eventDir, Path configDir,
                             Path logsDir) throws IOException {
        Path parent = zipPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        List<Path> eventFiles = collectExistingFiles(eventDir,
                "pending_items.jsonl",
                "sold_items.jsonl",
                "rejected_purchases.jsonl",
                "iloppis_metadata.json",
                "local_metadata.json");
        List<Path> configFiles = collectExistingFiles(configDir,
                "global.json",
                "iloppis-mode.json");
        List<Path> logFiles = collectLogFiles(logsDir);

        try (ZipOutputStream zos = new ZipOutputStream(
                Files.newOutputStream(zipPath), StandardCharsets.UTF_8)) {
            JSONObject manifest = new JSONObject();
            manifest.put("cashierName", cashierName);
            manifest.put("eventId", eventId);
            manifest.put("eventName", eventName != null ? eventName : "");
            manifest.put("exportTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            manifest.put("format", "iloppis-support-bundle-v1");
            manifest.put("purpose", "support");
            manifest.put("eventFiles", toRelativeNames(eventFiles, eventDir));
            manifest.put("configFiles", toRelativeNames(configFiles, configDir));
            manifest.put("logFiles", toRelativeNames(logFiles, logsDir));

            addTextEntry(zos, "manifest.json", manifest.toString(2));

            for (Path file : eventFiles) {
                addBundleFileEntry(zos, "event/" + file.getFileName(), file);
            }
            for (Path file : configFiles) {
                addBundleFileEntry(zos, "config/" + file.getFileName(), file);
            }
            for (Path file : logFiles) {
                addFileEntry(zos, "logs/" + file.getFileName(), file);
            }
        }
    }

    private static int getBundleEntryCount(String eventId) throws IOException {
        int fileCount = collectExistingFiles(LocalEventPaths.getEventDir(eventId),
                "pending_items.jsonl",
                "sold_items.jsonl",
                "rejected_purchases.jsonl",
                "iloppis_metadata.json",
                "local_metadata.json").size();
        fileCount += collectExistingFiles(AppPaths.getConfigDir(),
                "global.json",
                "iloppis-mode.json").size();
        fileCount += collectLogFiles(AppPaths.getLogsDir()).size();
        return fileCount + 1; // manifest.json
    }

    private static List<Path> collectExistingFiles(Path parent, String... names) {
        List<Path> files = new ArrayList<>();
        for (String name : names) {
            Path path = parent.resolve(name);
            if (Files.exists(path) && Files.isRegularFile(path)) {
                files.add(path);
            }
        }
        return files;
    }

    private static List<Path> collectLogFiles(Path logsDir) throws IOException {
        if (Files.notExists(logsDir) || !Files.isDirectory(logsDir)) {
            return List.of();
        }

        try (var stream = Files.list(logsDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("loppiskassan.log"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static List<String> toRelativeNames(List<Path> files, Path baseDir) {
        List<String> names = new ArrayList<>(files.size());
        for (Path file : files) {
            names.add(baseDir.relativize(file).toString());
        }
        return names;
    }

    private static void addTextEntry(ZipOutputStream zos, String name, String content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static void addBundleFileEntry(ZipOutputStream zos, String entryName, Path file) throws IOException {
        if (shouldSanitizeJson(file)) {
            addTextEntry(zos, entryName, sanitizeJsonContent(file));
            return;
        }
        addFileEntry(zos, entryName, file);
    }

    private static void addFileEntry(ZipOutputStream zos, String name, Path file) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        Files.copy(file, zos);
        zos.closeEntry();
    }

    private static boolean shouldSanitizeJson(Path file) {
        String fileName = file.getFileName().toString();
        return "iloppis-mode.json".equals(fileName) || "iloppis_metadata.json".equals(fileName);
    }

    private static String sanitizeJsonContent(Path file) throws IOException {
        try {
            JSONObject json = new JSONObject(Files.readString(file, StandardCharsets.UTF_8));
            removeSensitiveKeys(json, Set.of("apiKey"));
            return json.toString(2);
        } catch (Exception e) {
            JSONObject fallback = new JSONObject();
            fallback.put("sourceFile", file.getFileName().toString());
            fallback.put("omitted", true);
            fallback.put("reason", "Could not sanitize JSON safely");
            return fallback.toString(2);
        }
    }

    private static void removeSensitiveKeys(JSONObject json, Set<String> keysToRemove) {
        for (String key : keysToRemove) {
            json.remove(key);
        }
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
