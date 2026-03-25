package se.goencoder.loppiskassan.util;

import java.util.Set;

public final class ArchiveFileNames {
    private static final String STABLE_PREFIX = "archive_";
    private static final Set<String> LEGACY_PREFIXES = Set.of("arkiverade_", "archived_");

    private ArchiveFileNames() {}

    public static String createFileName(String timestamp) {
        return STABLE_PREFIX + timestamp + ".csv";
    }

    public static boolean isArchiveFileName(String fileName) {
        if (fileName == null || !fileName.endsWith(".csv")) {
            return false;
        }
        if (fileName.startsWith(STABLE_PREFIX)) {
            return true;
        }
        return LEGACY_PREFIXES.stream().anyMatch(fileName::startsWith);
    }
}
