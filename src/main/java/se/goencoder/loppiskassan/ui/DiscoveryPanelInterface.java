package se.goencoder.loppiskassan.ui;

import se.goencoder.iloppis.model.Event;

import java.util.List;

/**
 * Panel interface for the "Discovery"/"Konfiguration" tab.
 */
public interface DiscoveryPanelInterface extends SelectabableTab {
    /**
     * Clears the events table.
     */
    void clearEventsTable();

    /**
     * Populates the events table with the given list of events.
     * @param events
     */
    void populateEventsTable(List<Event> events);

    /**
     * Returns the index of the currently selected row in the events table.
     * @return
     */
    int getSelectedTableRow();

    /**
     * Returns the event ID for the given row in the events table.
     * @param rowIndex
     * @return
     */
    String getEventIdForRow(int rowIndex);

    /**
     * Returns the cashier code entered by the user.
     * @return
     */
    String getCashierCode();

    /**
     * Sets the name of the event in the UI.
     */
    void setEventName(String name);

    /**
     * Sets the description of the event in the UI.
     */
    void setEventDescription(String description);

    /**
     * Sets the address of the event in the UI.
     */
    void setEventAddress(String address);

    /**
     * Sets whether the "offline" event is currently selected or not.
     */
    void setOfflineMode(boolean offline);

    /**
     * Enable/disable the manual edit of revenue splits (only allowed if offline).
     */
    void setRevenueSplitEditable(boolean editable);

    /**
     * Sets each part of the revenue split in the UI.
     */
    void setRevenueSplit(float marketOwner, float vendor, float platform);

    /**
     * Returns the user-entered market owner share (if offline).
     */
    int getMarketOwnerSplit();

    /**
     * Returns the user-entered vendor share (if offline).
     */
    int getVendorSplit();

    /**
     * Returns the user-entered platform owner share (if offline).
     */
    int getPlatformSplit();

    /**
     * Enables/disables the "Öppna Kassa" button.
     */
    void setCashierButtonEnabled(boolean enabled);

    /**
     * Clears the cashier code field.
     */
    void clearCashierCodeField();

    // Switch between "noSelection" label and the detail form card
    void showDetailForm(boolean show);

    // Show or hide "Kassakod" label + field
    void showCashierCode(boolean show);
    /**
     * Switches the UI to "active event" mode if true,
     * or back to "normal selection" mode if false.
     */
    void setRegisterOpened(boolean opened);

    /**
     * Displays basic info about the active event in the UI,
     * e.g. name, address, etc.
     */
    void showActiveEventInfo(String eventName, String description, String address);

    /**
     * Called by the controller to show/hide the "Change event" button.
     */
    void setChangeEventButtonVisible(boolean visible);
}
