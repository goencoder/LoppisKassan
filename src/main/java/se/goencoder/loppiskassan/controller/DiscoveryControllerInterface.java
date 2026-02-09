package se.goencoder.loppiskassan.controller;

import se.goencoder.loppiskassan.ui.DiscoveryPanelInterface;

/**
 * Controller contract for discovering events and opening the register.
 * <p>
 * Mode behavior:
 * <ul>
 *   <li><b>Online:</b> fetch events from the backend; opening the register requires a valid cashier code.</li>
 *   <li><b>Local:</b> list locally configured events; opening the register ignores cashier code and uses local defaults.</li>
 * </ul>
 */
public interface DiscoveryControllerInterface {

    /**
     * Register the associated view.
     * @param view discovery panel view
     */
    void registerView(DiscoveryPanelInterface view);

    /**
     * Discover events visible to the user starting from an optional date.
     * Online mode should query the backend; local mode should read local storage.
     *
     * @param dateFrom ISO-8601 date string (inclusive) or empty for all
     */
    void discoverEvents(String dateFrom);

    /**
     * Open the register for a selected event.
     * <p>
     * <b>Online:</b> {@code cashierCode} must be present and validated.
     * <b>Local:</b> {@code cashierCode} may be ignored and the register opens immediately.
     *
     * @param eventId unique event identifier
     * @param cashierCode cashier/ API code (required online)
     */
    void openRegister(String eventId, String cashierCode);

    /**
     * Notify that the user selected a new event in the UI.
     * Controller should update detail fields and validation state.
     *
     * @param eventId selected event identifier
     */
    void eventSelected(String eventId);

    /**
     * Initialize the discovery view to a consistent default state
     * (clear tables, hide details, set mode, etc.).
     */
    void initUIState();

    /**
     * Handle the user request to change the currently active event.
     * Typically returns the UI to the discovery list and resets register state.
     */
    void changeEventRequested();

    /**
     * Handle user request to upload a local event to iLoppis backend.
     * Shows BulkUploadDialog for event selection, code entry, and upload.
     *
     * @param eventId local event ID to upload
     */
    void uploadLocalEventRequested(String eventId);

    /**
     * Persist edits to a local event (name/description/address/split).
     *
     * @param eventId local event id
     * @param name event name
     * @param description event description
     * @param address combined address string (street, city)
     * @param marketOwner market owner percentage
     * @param vendor vendor percentage
     * @param platform platform percentage
     */
    void saveLocalEventEdits(String eventId, String name, String description, String address,
                             float marketOwner, float vendor, float platform);

    /**
     * Delete a local event and all its data.
     * @param eventId the event ID to delete
     */
    void deleteLocalEvent(String eventId);
}
