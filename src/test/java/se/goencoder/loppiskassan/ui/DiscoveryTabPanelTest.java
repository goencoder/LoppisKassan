package se.goencoder.loppiskassan.ui;

import org.junit.jupiter.api.Test;
import se.goencoder.iloppis.model.V1Event;
import se.goencoder.iloppis.model.V1RevenueSplit;

import javax.swing.SwingUtilities;
import javax.swing.JLabel;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiscoveryTabPanelTest {

    @Test
    void showActiveEventInfo_toleratesNullSplitAndPopulatesDefaults() throws Exception {
        DiscoveryTabPanel panel = createPanel();

        V1Event event = new V1Event();
        event.setName("Testevent");
        event.setDescription("Desc");
        event.setAddressStreet("Street 1");
        event.setAddressCity("City");

        // Act: no split available
        SwingUtilities.invokeAndWait(() -> panel.showActiveEventInfo(event, null));

        assertEquals("Testevent", labelText(panel, "activeEventNameLabel"));
        assertEquals("Desc", labelText(panel, "activeEventDescLabel"));
        assertEquals("Street 1, City", labelText(panel, "activeEventAddressLabel"));
        assertEquals("0", labelText(panel, "marketOwnerSplitLabel"));
        assertEquals("0", labelText(panel, "vendorSplitLabel"));
        assertEquals("0", labelText(panel, "platformSplitLabel"));
    }

    @Test
    void showActiveEventInfo_usesProvidedSplitValues() throws Exception {
        DiscoveryTabPanel panel = createPanel();

        V1Event event = new V1Event();
        event.setName("Testevent");
        event.setDescription("Desc");
        event.setAddressStreet("Street 1");
        event.setAddressCity("City");

        V1RevenueSplit split = new V1RevenueSplit();
        split.setMarketOwnerPercentage(10f);
        split.setVendorPercentage(90f);
        split.setPlatformProviderPercentage(0f);

        SwingUtilities.invokeAndWait(() -> panel.showActiveEventInfo(event, split));

        assertEquals("10", labelText(panel, "marketOwnerSplitLabel"));
        assertEquals("90", labelText(panel, "vendorSplitLabel"));
        assertEquals("0", labelText(panel, "platformSplitLabel"));
    }

    private DiscoveryTabPanel createPanel() throws Exception {
        AtomicReference<DiscoveryTabPanel> panelRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> panelRef.set(new DiscoveryTabPanel()));
        return panelRef.get();
    }

    private String labelText(DiscoveryTabPanel panel, String fieldName) throws Exception {
        Field field = DiscoveryTabPanel.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        JLabel label = (JLabel) field.get(panel);
        return label.getText();
    }
}
