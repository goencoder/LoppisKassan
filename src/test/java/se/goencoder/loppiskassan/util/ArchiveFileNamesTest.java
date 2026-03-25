package se.goencoder.loppiskassan.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveFileNamesTest {

    @Test
    void recognizesStableArchiveFileNames() {
        assertTrue(ArchiveFileNames.isArchiveFileName("archive_26-03-25_17-49-12.csv"));
    }

    @Test
    void recognizesLegacyLocalizedArchiveFileNames() {
        assertTrue(ArchiveFileNames.isArchiveFileName("arkiverade_26-03-25_17-49-12.csv"));
        assertTrue(ArchiveFileNames.isArchiveFileName("archived_26-03-25_17-49-12.csv"));
    }

    @Test
    void rejectsNonArchiveFiles() {
        assertFalse(ArchiveFileNames.isArchiveFileName("sales.csv"));
        assertFalse(ArchiveFileNames.isArchiveFileName("archive_26-03-25_17-49-12.json"));
    }
}
