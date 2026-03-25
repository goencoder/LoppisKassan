package se.goencoder.loppiskassan.storage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CachedOnlineEventTest {

    @Test
    void toJsonStringOmitsApiKeyFromMetadata() {
        CachedOnlineEvent cached = new CachedOnlineEvent(
                "event-1",
                "Testloppis",
                "Beskrivning",
                "Gatan 1",
                "Stockholm",
                "market-1",
                "secret-key",
                "[1,2,3]",
                "{\"marketOwner\":10}",
                OffsetDateTime.parse("2026-03-25T10:15:30+01:00"),
                OffsetDateTime.parse("2026-03-25T16:15:30+01:00"),
                OffsetDateTime.parse("2026-03-24T09:00:00+01:00")
        );

        String json = cached.toJsonString();

        assertFalse(json.contains("apiKey"));
        assertFalse(json.contains("secret-key"));
        assertTrue(json.contains("\"eventId\":\"event-1\""));
    }

    @Test
    void fromJsonStringReadsLegacyApiKeyForMigration() throws IOException {
        String json = """
                {
                  "eventId": "event-1",
                  "eventName": "Testloppis",
                  "apiKey": "legacy-secret"
                }
                """;

        CachedOnlineEvent cached = CachedOnlineEvent.fromJsonString(json);

        assertEquals("legacy-secret", cached.getApiKey());
    }
}
