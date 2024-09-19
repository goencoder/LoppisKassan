package se.goencoder.loppiskassan.records;

import se.goencoder.loppiskassan.ui.Popup;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.nio.file.StandardCopyOption.*;

public class FileHelper {
    private static final Logger logger = Logger.getLogger(FileHelper.class.getName());
    private static final String baseDir = System.getProperty("user.dir");
    public static final String LOPPISKASSAN_CSV = "loppiskassan.csv";
    private static final String[] backupRecordsFileNames = new String[10];
    private static int backupRecordsIndex = Math.abs(new Random(System.currentTimeMillis()).nextInt() % 10);

    static {
        for (int i = 0; i < backupRecordsFileNames.length; i++) {
            backupRecordsFileNames[i] = LOPPISKASSAN_CSV + ".backup." + i;
        }
    }

    public static void createDirectories() throws IOException {
        try {
            Files.createDirectories(getFilePath("logs"));
            Files.createDirectories(getFilePath("data"));
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to create directories", e);
            throw e;
        }
    }

    private static int nextBackupIndex() {
        backupRecordsIndex = ++backupRecordsIndex % 10;
        return backupRecordsIndex;
    }

    public static Path getNextRecordsBackupPath() {
        return getFilePath("data/" + backupRecordsFileNames[nextBackupIndex()]);
    }

    public static Path getRecordFilePath(String fileName) {
        return getFilePath("data/" + fileName);
    }

    public static Path getLogFilePath() {
        return getFilePath("logs/loppiskassan.log");
    }

    private static Path getFilePath(String relativePath) {
        return Paths.get(baseDir, relativePath);
    }

    public static boolean assertRecordFileRights(String fileName) {
        Path path = getRecordFilePath(fileName);
        if (Files.notExists(path)) {
            return true;
        }
        boolean canRead = Files.isReadable(path);
        boolean canWrite = Files.isWritable(path);
        if (!canWrite || !canRead) {
            String message = String.format("Cannot read/write file: %s. Writable: %b, Readable: %b", path, canWrite, canRead);
            logger.warning(message);
            Popup.WARNING.showAndWait("File Access Error", "Check read/write permissions and ensure no other program is using the file.");
            return false;
        }
        return true;
    }

    public static void saveToFile(String fileName, String leadingComment, String csv) throws IOException {
        Path path = getRecordFilePath(fileName);
        try (OutputStream outputStream = new BufferedOutputStream(Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
            if (Files.notExists(path) || Files.size(path) == 0) {
                csv = FormatHelper.CVS_HEADERS + FormatHelper.LINE_ENDING + csv;
            }
            byte[] byteArray = csv.getBytes(StandardCharsets.UTF_8);
            if (leadingComment != null && !leadingComment.isEmpty()) {
                byte[] comment = leadingComment.getBytes(StandardCharsets.UTF_8);
                outputStream.write(comment);
                outputStream.write(FormatHelper.LINE_ENDING.getBytes(StandardCharsets.UTF_8));
            }
            outputStream.write(byteArray);
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to save data to file: " + csv, ex);
            throw ex;
        }
    }

    public static void createBackupFile() throws IOException {
        Path source = getRecordFilePath(LOPPISKASSAN_CSV);
        if (Files.exists(source)) {
            Path backupFile = getNextRecordsBackupPath();
            Files.move(source, backupFile, ATOMIC_MOVE, REPLACE_EXISTING);
            logger.log(Level.INFO, "Created backup file: " + backupFile);
        }
    }

    public static String readFromFile(String fileName) throws IOException {
        Path path = getRecordFilePath(fileName);
        if (!Files.exists(path)) {
            logger.log(Level.INFO, "File does not exist: " + path);
            Popup.INFORMATION.showAndWait("Filen finns inte",
                    "Filen " + fileName + " finns inte. Har du registrerat några köp?");
            return "";
        }
        return readFromFile(path);
    }

    public static String readFromFile(Path path) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                // if line is a comment, ignore it
                if (line.startsWith("#")) {
                    continue;
                }
                content.append(line).append(FormatHelper.LINE_ENDING);
            }
            return content.toString();
        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Failed to read from file", ex);
            throw ex;
        }
    }
}
