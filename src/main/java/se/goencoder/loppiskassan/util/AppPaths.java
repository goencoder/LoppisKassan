package se.goencoder.loppiskassan.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Centralized application paths.
 *
 * All persistent data is stored under ~/.loppiskassan to avoid ambiguity
 * when the app is started from different working directories.
 */
public final class AppPaths {
    private static final Logger log = Logger.getLogger(AppPaths.class.getName());

    private static final String BASE_DIR_NAME = ".loppiskassan";
    private static final String CONFIG_DIR_NAME = "config";
    private static final String LOGS_DIR_NAME = "logs";
    private static final String DATA_DIR_NAME = "data";

    private AppPaths() {}

    public static Path getBaseDir() {
        String homeDir = System.getProperty("user.home");
        return Paths.get(homeDir, BASE_DIR_NAME);
    }

    public static Path getConfigDir() {
        return getBaseDir().resolve(CONFIG_DIR_NAME);
    }

    public static Path getLogsDir() {
        return getBaseDir().resolve(LOGS_DIR_NAME);
    }

    public static Path getDataDir() {
        return getBaseDir().resolve(DATA_DIR_NAME);
    }

    public static Path getLegacyConfigDir() {
        return Paths.get(CONFIG_DIR_NAME);
    }

    public static Path getLegacyLogsDir() {
        return Paths.get(LOGS_DIR_NAME);
    }

    public static Path getLegacyDataDir() {
        return Paths.get(DATA_DIR_NAME);
    }

    /**
     * Move legacy config/logs/data directories (relative to old working dir)
     * into the unified ~/.loppiskassan folder when possible.
     */
    public static void migrateLegacyPaths() {
        migrateDirectory(getLegacyConfigDir(), getConfigDir());
        migrateDirectory(getLegacyLogsDir(), getLogsDir());
        migrateDirectory(getLegacyDataDir(), getDataDir());
    }

    private static void migrateDirectory(Path legacyDir, Path targetDir) {
        if (legacyDir == null || targetDir == null) {
            return;
        }
        if (Files.notExists(legacyDir)) {
            return;
        }
        if (!Files.isDirectory(legacyDir)) {
            return;
        }

        try {
            Files.createDirectories(targetDir);
        } catch (IOException e) {
            log.warning("Failed to create target directory: " + targetDir + " (" + e.getMessage() + ")");
            return;
        }

        try (Stream<Path> paths = Files.list(legacyDir)) {
            paths.forEach(path -> {
                Path dest = targetDir.resolve(path.getFileName());
                if (Files.exists(dest)) {
                    return;
                }
                try {
                    Files.move(path, dest, StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException e) {
                    log.warning("Failed to move legacy file " + path + " -> " + dest + " (" + e.getMessage() + ")");
                }
            });
        } catch (IOException e) {
            log.warning("Failed to list legacy directory: " + legacyDir + " (" + e.getMessage() + ")");
        }

        try (Stream<Path> remaining = Files.list(legacyDir)) {
            if (remaining.findAny().isEmpty()) {
                Files.deleteIfExists(legacyDir);
            }
        } catch (IOException e) {
            log.warning("Failed to cleanup legacy directory: " + legacyDir + " (" + e.getMessage() + ")");
        }
    }
}
