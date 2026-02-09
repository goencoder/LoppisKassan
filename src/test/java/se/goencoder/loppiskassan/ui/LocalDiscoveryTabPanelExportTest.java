package se.goencoder.loppiskassan.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.goencoder.iloppis.model.V1Event;
import se.goencoder.iloppis.model.V1RevenueSplit;
import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.storage.LocalEvent;
import se.goencoder.loppiskassan.storage.LocalEventType;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.storage.LocalEventRepository;

import javax.swing.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for export button visibility in <b>LocalDiscoveryTabPanel</b>.
 * Export now lives exclusively in local mode.
 */
class LocalDiscoveryTabPanelExportTest {

    @TempDir
    Path tempDir;

    private LocalDiscoveryTabPanel panel;
    private String localEventWithSalesId;
    private String localEventEmptyId;

    @BeforeEach
    void setUp() throws Exception {
        System.setProperty("user.home", tempDir.toString());
        setupTestEvents();
        SwingUtilities.invokeAndWait(() -> panel = new LocalDiscoveryTabPanel());
    }

    private void setupTestEvents() throws IOException {
        localEventWithSalesId = "local-test-with-sales";
        LocalEvent eventWithSales = new LocalEvent(
                localEventWithSalesId, LocalEventType.LOCAL,
                "Local Event With Sales", "Test event with sales",
                "Test Street", "Test City",
                OffsetDateTime.now(), new V1RevenueSplit());
        LocalEventRepository.save(eventWithSales);

        V1SoldItem item = new V1SoldItem(1, 50, V1PaymentMethod.Kontant);
        Path pendingItemsPath = LocalEventPaths.getPendingItemsPath(localEventWithSalesId);
        JsonlHelper.writeItems(pendingItemsPath, List.of(item));

        localEventEmptyId = "local-test-empty";
        LocalEvent emptyEvent = new LocalEvent(
                localEventEmptyId, LocalEventType.LOCAL,
                "Local Event Empty", "Empty test event",
                "Test Street", "Test City",
                OffsetDateTime.now(), new V1RevenueSplit());
        LocalEventRepository.save(emptyEvent);
    }

    @Test
    void testExportButtonVisibilityForLocalEventWithSales() throws Exception {
        V1Event localEvent = new V1Event();
        localEvent.setId(localEventWithSalesId);
        localEvent.setName("Local Event With Sales");
        localEvent.setAddressStreet("Test Street");
        localEvent.setAddressCity("Test City");

        V1RevenueSplit split = new V1RevenueSplit();
        split.setMarketOwnerPercentage(10f);
        split.setVendorPercentage(85f);
        split.setPlatformProviderPercentage(5f);

        SwingUtilities.invokeAndWait(() -> panel.showActiveEventInfo(localEvent, split));

        JButton exportButton = getButton("exportDataButton");
        JButton csvButton = getButton("csvExportButton");

        assertTrue(exportButton.isVisible(), "JSONL export button should be visible for local event with sales");
        assertTrue(csvButton.isVisible(), "CSV export button should be visible for local event with sales");
    }

    @Test
    void testExportButtonHiddenForLocalEventWithoutSales() throws Exception {
        V1Event localEvent = new V1Event();
        localEvent.setId(localEventEmptyId);
        localEvent.setName("Local Event Empty");
        localEvent.setAddressStreet("Test Street");
        localEvent.setAddressCity("Test City");

        V1RevenueSplit split = new V1RevenueSplit();
        split.setMarketOwnerPercentage(10f);
        split.setVendorPercentage(85f);
        split.setPlatformProviderPercentage(5f);

        SwingUtilities.invokeAndWait(() -> panel.showActiveEventInfo(localEvent, split));

        JButton exportButton = getButton("exportDataButton");
        JButton csvButton = getButton("csvExportButton");

        assertFalse(exportButton.isVisible(), "JSONL export button should be hidden for local event without sales");
        assertFalse(csvButton.isVisible(), "CSV export button should be hidden for local event without sales");
    }

    @Test
    void testNoExportButtonOnILoppisPanel() throws Exception {
        // DiscoveryTabPanel (iLoppis mode) should NOT have export buttons at all
        DiscoveryTabPanel iloppisPanel = new DiscoveryTabPanel();
        assertThrows(NoSuchFieldException.class,
                () -> DiscoveryTabPanel.class.getDeclaredField("exportDataButton"),
                "iLoppis panel should not have an export button");
    }

    private JButton getButton(String fieldName) throws Exception {
        Field f = LocalDiscoveryTabPanel.class.getDeclaredField(fieldName);
        f.setAccessible(true);
        return (JButton) f.get(panel);
    }
}
