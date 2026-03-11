package se.goencoder.loppiskassan.storage;

import org.junit.jupiter.api.Test;
import se.goencoder.iloppis.model.V1RevenueSplit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LocalEventRepositoryTest {

    @Test
    void createAndLoadLocalEvent() throws Exception {
        String originalHome = System.getProperty("user.home");
        Path tempHome = Files.createTempDirectory("loppiskassan-home");
        System.setProperty("user.home", tempHome.toString());

        try {
            V1RevenueSplit split = new V1RevenueSplit()
                    .marketOwnerPercentage(10.0f)
                    .vendorPercentage(85.0f)
                    .platformProviderPercentage(5.0f)
                    .charityPercentage(0.0f);

            LocalEvent event = new LocalEvent(
                    "local-test-1",
                    LocalEventType.LOCAL,
                    "Test Event",
                    "Local test event",
                    "Main Street 1",
                    "Test City",
                    OffsetDateTime.of(2026, 2, 8, 10, 0, 0, 0, ZoneOffset.UTC),
                    split
            );

            LocalEventRepository.create(event);

            Path metadataPath = LocalEventPaths.getMetadataPath(event.getEventId());
            Path pendingPath = LocalEventPaths.getPendingItemsPath(event.getEventId());
            Path soldPath = LocalEventPaths.getSoldItemsPath(event.getEventId());

            assertTrue(Files.exists(metadataPath));
            assertTrue(Files.exists(pendingPath));
            assertTrue(Files.exists(soldPath));

            LocalEvent loaded = LocalEventRepository.load(event.getEventId());
            assertNotNull(loaded);
            assertEquals(event.getEventId(), loaded.getEventId());
            assertEquals(event.getName(), loaded.getName());
            assertEquals(event.getDescription(), loaded.getDescription());
            assertEquals(event.getEventType(), loaded.getEventType());
            assertEquals(event.getRevenueSplit().getVendorPercentage(), loaded.getRevenueSplit().getVendorPercentage());

            List<LocalEvent> all = LocalEventRepository.loadAll();
            assertEquals(1, all.size());
        } finally {
            System.setProperty("user.home", originalHome);
        }
    }
}
