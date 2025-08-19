package se.goencoder.loppiskassan.ui;

import se.goencoder.iloppis.model.Event;
import se.goencoder.iloppis.model.RevenueSplit;

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
     * Enables/disables the "Öppna Kassa" button.
     */
    void setCashierButtonEnabled(boolean enabled);

    /**
     * Clears the cashier code field.
     */
    void clearCashierCodeField();

    // Switch between "noSelection" label and the detail form card
    void showDetailForm(boolean show);


    /**
     * Switches the UI to "active event" mode if true,
     * or back to "normal selection" mode if false.
     */
    void setRegisterOpened(boolean opened);

    /**
     * Displays basic info about the active event in the UI,
     * e.g. name, address, etc.
     */
    void showActiveEventInfo(Event event, RevenueSplit split);

    /**
     * Called by the controller to show/hide the "Change event" button.
     */
    void setChangeEventButtonVisible(boolean visible);


}
