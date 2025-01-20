package se.goencoder.loppiskassan.controller;

import se.goencoder.loppiskassan.ui.DiscoveryPanelInterface;

import java.util.Set;

public interface DiscoveryControllerInterface {

    /**
     * Register a view with the controller.
     * @param view The view to register
     */
    void registerView(DiscoveryPanelInterface view);

    /**
     * Called when the user clicks "Upptäck event".
     * @param dateFrom
     */
    void discoverEvents(String dateFrom);

    /**
     * Called when the user clicks "Hämta API-nyckel".
     * @param eventId
     * @param cashierCode
     */
    void openRegister(String eventId, String cashierCode);

    /**
     * Called when the user clicks "Hämta API-nyckel".
     * @param eventId
     * @return
     */
    Set<Integer> getApprovedSellersForEvent(String eventId);

    /**
     * Called when the user selects an event from the table (by row).
     * The eventId might be "offline" (synthetic) or a real ID from the API.
     */
    void eventSelected(String eventId);

    /**
     * If the user is editing revenue splits (i.e., offline mode),
     * the UI calls this so the controller can store them in ConfigurationStore.
     */
    void setRevenueSplitFromUI(float marketOwner, float vendor, float platform);

    /**
     * Init UI state
     */
    void initUIState();

    /**
     * Called when the user clicks "Ändra event".
     */
    void changeEventRequested();
}
