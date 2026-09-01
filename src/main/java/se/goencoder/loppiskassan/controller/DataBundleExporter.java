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
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates a ZIP bundle with troubleshooting data for iLoppis support.
 */
public class DataBundleExporter {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss");
    private static final String DEFAULT_CASHIER_SLUG = "kassa";
    private static final String LOG_FILE_BASENAME = "loppiskassan.log";

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
            int entryCount = getBundleEntryCountBestEffort(eventId);

            Component parent = DialogService.getDialogParent();
            ExportDataDialog dialog = new ExportDataDialog(
                    parent,
                    defaultFileName,
                    entryCount,
                    ".zip",
                    "support_bundle.dialog.title",
                    "support_bundle.dialog.tip");
            File destination = dialog.showDialog();
            if (destination == null) {
                return;
            }

            BundleCreationResult result = createBundle(
                    destination.toPath(), eventId, eventName, cashierName);

            if (result.isPartial()) {
                Popup.WARNING.showAndWait(
                        LocalizationManager.tr("support_bundle.partial.title"),
                        LocalizationManager.tr("support_bundle.partial.message",
                                destination.getName(),
                                destination.getParent(),
                                cashierName)
                );
            } else {
                Popup.INFORMATION.showAndWait(
                        LocalizationManager.tr("support_bundle.success.title"),
                        LocalizationManager.tr("support_bundle.success.message",
                                destination.getName(),
                                destination.getParent(),
                                cashierName)
                );
            }
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

    static BundleCreationResult createBundle(Path zipPath, String eventId, String eventName,
                                             String cashierName) throws IOException {
        return createBundle(zipPath, eventId, eventName, cashierName,
                LocalEventPaths.getEventDir(eventId),
                AppPaths.getConfigDir(),
                AppPaths.getLogsDir());
    }

    static BundleCreationResult createBundle(Path zipPath, String eventId, String eventName,
                                             String cashierName, Path eventDir, Path configDir,
                                             Path logsDir) throws IOException {
        return createBundle(zipPath, eventId, eventName, cashierName,
                eventDir, configDir, logsDir, Files::readAllBytes);
    }

    static BundleCreationResult createBundle(Path zipPath, String eventId, String eventName,
                                             String cashierName, Path eventDir, Path configDir,
                                             Path logsDir, LogFileReader logFileReader) throws IOException {
        Path absoluteZipPath = zipPath.toAbsolutePath();
        Path parent = absoluteZipPath.getParent();
        Files.createDirectories(parent);

        List<Path> eventFiles = collectExistingFiles(eventDir,
                "pending_items.jsonl",
                "rejected_purchases.jsonl",
                "iloppis_metadata.json",
                "local_metadata.json");
        List<Path> configFiles = collectExistingFiles(configDir,
                "global.json",
                "iloppis-mode.json");
        flushActiveFileHandlers();
        LogCollection logs = snapshotLogFiles(logsDir, logFileReader);
        Path temporaryZip = Files.createTempFile(parent,
                "." + absoluteZipPath.getFileName() + "-", ".tmp");

        try {
            try (ZipOutputStream zos = new ZipOutputStream(
                    Files.newOutputStream(temporaryZip), StandardCharsets.UTF_8)) {
                JSONObject manifest = new JSONObject();
                manifest.put("cashierName", cashierName);
                manifest.put("eventId", eventId);
                manifest.put("eventName", eventName != null ? eventName : "");
                manifest.put("exportTime", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                manifest.put("format", "iloppis-support-bundle-v1");
                manifest.put("purpose", "support");
                manifest.put("eventFiles", toRelativeNames(eventFiles, eventDir));
                manifest.put("configFiles", toRelativeNames(configFiles, configDir));
                manifest.put("logFiles", logs.snapshots().stream()
                        .map(LogSnapshot::fileName)
                        .toList());
                manifest.put("skippedLogFiles", logs.skipped().stream()
                        .map(skipped -> new JSONObject()
                                .put("file", skipped.fileName())
                                .put("reason", skipped.reason()))
                        .toList());
                manifest.put("logCollectionStatus", logs.status());

                addTextEntry(zos, "manifest.json", manifest.toString(2));

                for (Path file : eventFiles) {
                    addBundleFileEntry(zos, "event/" + file.getFileName(), file);
                }
                for (Path file : configFiles) {
                    addBundleFileEntry(zos, "config/" + file.getFileName(), file);
                }
                for (LogSnapshot snapshot : logs.snapshots()) {
                    addBytesEntry(zos, "logs/" + snapshot.fileName(), snapshot.content());
                }
            }

            moveCompletedBundle(temporaryZip, absoluteZipPath);
        } finally {
            Files.deleteIfExists(temporaryZip);
        }

        return new BundleCreationResult(logs.snapshots().size(), logs.skipped().size());
    }

    private static int getBundleEntryCount(String eventId) throws IOException {
        int fileCount = collectExistingFiles(LocalEventPaths.getEventDir(eventId),
                "pending_items.jsonl",
                "rejected_purchases.jsonl",
                "iloppis_metadata.json",
                "local_metadata.json").size();
        fileCount += collectExistingFiles(AppPaths.getConfigDir(),
                "global.json",
                "iloppis-mode.json").size();
        fileCount += collectLogFiles(AppPaths.getLogsDir()).size();
        return fileCount + 1; // manifest.json
    }

    private static int getBundleEntryCountBestEffort(String eventId) {
        try {
            return getBundleEntryCount(eventId);
        } catch (IOException e) {
            return 0;
        }
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
        if (Files.notExists(logsDir)) {
            return List.of();
        }
        if (!Files.isDirectory(logsDir)) {
            throw new IOException("Log path is not an accessible directory");
        }

        try (var stream = Files.list(logsDir)) {
            return stream
                    .filter(path -> isLogDataFileName(path.getFileName().toString()))
                    .sorted(Comparator
                            .comparingInt(DataBundleExporter::logReadPriority)
                            .thenComparing(path -> path.getFileName().toString()))
                    .toList();
        }
    }

    private static int logReadPriority(Path path) {
        String fileName = path.getFileName().toString();
        return LOG_FILE_BASENAME.equals(fileName) || (LOG_FILE_BASENAME + ".0").equals(fileName)
                ? 1
                : 0;
    }

    static boolean isLogDataFileName(String fileName) {
        if (LOG_FILE_BASENAME.equals(fileName)) {
            return true;
        }
        if (!fileName.startsWith(LOG_FILE_BASENAME + ".")) {
            return false;
        }

        String suffix = fileName.substring(LOG_FILE_BASENAME.length() + 1);
        if (suffix.isEmpty()) {
            return false;
        }
        for (String part : suffix.split("\\.", -1)) {
            if (part.isEmpty() || !part.chars().allMatch(Character::isDigit)) {
                return false;
            }
        }
        return true;
    }

    private static LogCollection snapshotLogFiles(Path logsDir, LogFileReader logFileReader)
            throws IOException {
        List<LogSnapshot> snapshots = new ArrayList<>();
        List<SkippedLog> skipped = new ArrayList<>();
        for (Path file : collectLogFiles(logsDir)) {
            try {
                snapshots.add(new LogSnapshot(file.getFileName().toString(), logFileReader.read(file)));
            } catch (IOException e) {
                String reason = e instanceof AccessDeniedException ? "access_denied" : "io_error";
                skipped.add(new SkippedLog(file.getFileName().toString(), reason));
            }
        }
        String status;
        if (snapshots.isEmpty()) {
            status = "no_logs_included";
        } else if (!skipped.isEmpty()) {
            status = "partial";
        } else {
            status = "complete";
        }
        return new LogCollection(List.copyOf(snapshots), List.copyOf(skipped), status);
    }

    private static void flushActiveFileHandlers() {
        Logger rootLogger = Logger.getAnonymousLogger().getParent();
        for (Handler handler : rootLogger.getHandlers()) {
            if (handler instanceof FileHandler) {
                handler.flush();
            }
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
        addBytesEntry(zos, name, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void addBytesEntry(ZipOutputStream zos, String name, byte[] content) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(content);
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

    private static void moveCompletedBundle(Path source, Path destination) throws IOException {
        try {
            Files.move(source, destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @FunctionalInterface
    interface LogFileReader {
        byte[] read(Path path) throws IOException;
    }

    record BundleCreationResult(int includedLogCount, int skippedLogCount) {
        boolean isPartial() {
            return includedLogCount == 0 || skippedLogCount > 0;
        }
    }

    private record LogSnapshot(String fileName, byte[] content) {
    }

    private record SkippedLog(String fileName, String reason) {
    }

    private record LogCollection(List<LogSnapshot> snapshots, List<SkippedLog> skipped, String status) {
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
