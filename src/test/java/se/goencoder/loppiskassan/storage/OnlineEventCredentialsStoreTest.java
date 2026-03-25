package se.goencoder.loppiskassan.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnlineEventCredentialsStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void writeAndReadApiKeyUsesSeparateFile() {
        Path credentialsPath = tempDir.resolve("event").resolve("iloppis_credentials.json");

        OnlineEventCredentialsStore.writeApiKey(credentialsPath, "secret-key");

        assertTrue(Files.exists(credentialsPath));
        assertEquals("secret-key", OnlineEventCredentialsStore.readApiKey(credentialsPath));
    }

    @Test
    void writeApiKeyDeletesFileWhenCredentialIsCleared() throws Exception {
        Path credentialsPath = tempDir.resolve("event").resolve("iloppis_credentials.json");
        Files.createDirectories(credentialsPath.getParent());
        Files.writeString(credentialsPath, "{\"apiKey\":\"secret-key\"}");

        OnlineEventCredentialsStore.writeApiKey(credentialsPath, null);

        assertFalse(Files.exists(credentialsPath));
        assertNull(OnlineEventCredentialsStore.readApiKey(credentialsPath));
    }
}
