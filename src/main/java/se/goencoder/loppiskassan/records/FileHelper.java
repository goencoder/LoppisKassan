package se.goencoder.loppiskassan.records;

import se.goencoder.loppiskassan.util.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileHelper {
    private static final Logger logger = Logger.getLogger(FileHelper.class.getName());
    private static final Path baseDir = AppPaths.getBaseDir();

    public static void createDirectories() throws IOException {
        try {
            Files.createDirectories(getFilePath("logs"));
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Failed to create directories", e);
            throw e;
        }
    }

    public static Path getLogFilePath() {
        return getFilePath("logs/loppiskassan.log");
    }

    private static Path getFilePath(String relativePath) {
        return baseDir.resolve(relativePath);
    }
}
