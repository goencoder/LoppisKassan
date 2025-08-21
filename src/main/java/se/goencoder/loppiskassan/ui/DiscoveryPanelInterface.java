package se.goencoder.loppiskassan.ui;

import se.goencoder.iloppis.model.Event;
import se.goencoder.iloppis.model.RevenueSplit;

import java.util.List;

/**
 * View contract for discovering and selecting events (loppisar) and opening the register.
 * <p>
 * Mode awareness:
 * <ul>
 *   <li><b>Online:</b> shows remote events and requires a valid cashier code to open the register.</li>
 *   <li><b>Offline:</b> shows locally configured events and should gate features accordingly.</li>
 * </ul>
 */
public interface DiscoveryPanelInterface extends SelectabableTab {

    /** Clear the events table. */
    void clearEventsTable();

    /**
     * Populate the events table with discovered events.
     * @param events events to display (may be empty)
     */
    void populateEventsTable(List<Event> events);

    /**
     * Set the selected event name in the detail section.
     * @param name event name
     */
    void setEventName(String name);

    /**
     * Set the selected event description in the detail section.
     * @param description free-text description
     */
    void setEventDescription(String description);

    /**
     * Set the selected event address (single-line display string).
     * @param address formatted address
     */
    void setEventAddress(String address);

    /**
     * Indicate whether the UI is in offline mode.
     * Implementations should hide/disable online-only controls (e.g., API key/ cashier code).
     *
     * @param offline {@code true} for offline mode, {@code false} for online
     */
    void setOfflineMode(boolean offline);

    /**
     * Enable/disable editing of revenue split percentages.
     * Typically editable offline; read-only when sourced from the server online.
     *
     * @param editable {@code true} to allow editing
     */
    void setRevenueSplitEditable(boolean editable);

    /**
     * Update the displayed revenue split for market owner/ vendor/ platform.
     *
     * @param marketOwner percentage for the market owner (0–100)
     * @param vendor percentage for the vendor (0–100)
     * @param platform percentage for the platform (0–100)
     */
    void setRevenueSplit(float marketOwner, float vendor, float platform);

    /**
     * Enable/disable the "Open Register" button depending on validation state
     * (e.g., event selected + valid cashier code in online mode).
     *
     * @param enabled {@code true} to enable, {@code false} to disable
     */
    void setCashierButtonEnabled(boolean enabled);

    /**
     * Clear the cashier code input field.
     * Typically used after successfully opening the register or when changing event.
     */
    void clearCashierCodeField();

    /**
     * Show or hide the event details form area.
     * @param show {@code true} to show, {@code false} to hide
     */
    void showDetailForm(boolean show);

    /**
     * Reflect whether the register is currently open for the active event.
     * @param opened {@code true} if open, otherwise {@code false}
     */
    void setRegisterOpened(boolean opened);

    /**
     * Display basic information about the active event and its revenue split.
     * @param event the active event
     * @param split the associated revenue split
     */
    void showActiveEventInfo(Event event, RevenueSplit split);

    /**
     * Show or hide the "Change event" button allowing the user to pick a different event.
     * @param visible {@code true} to show, {@code false} to hide
     */
    void setChangeEventButtonVisible(boolean visible);
}
