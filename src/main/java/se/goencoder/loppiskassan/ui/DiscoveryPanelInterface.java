package se.goencoder.loppiskassan.ui;

import se.goencoder.iloppis.model.Event;

import java.util.List;

public interface DiscoveryPanelInterface extends SelectabableTab {
    // Called by controller to clear the table before new data is loaded
    void clearEventsTable();

    // Called by controller to populate the table with discovered events
    void populateEventsTable(List<Event> events);

    // Called by controller to display a status or error message in the UI
    void showStatusMessage(String message);

    // The user might need to read the dateFrom or cashierCode from the text fields, but
    // if the UI just passes them to the controller on button click, we might not need them in the interface.
    // For example, we can do the real reading in the panel's ActionListener and pass it to the controller.

    // The panel can optionally provide these if the controller needs to query them:
    int getSelectedTableRow();
    String getEventIdForRow(int rowIndex);
    String getCashierCode();
}
