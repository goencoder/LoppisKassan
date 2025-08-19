package se.goencoder.loppiskassan.controller;

import se.goencoder.loppiskassan.ui.DiscoveryPanelInterface;



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
     * Called when the user selects an event from the table (by row).
     * The eventId might be "offline" (synthetic) or a real ID from the API.
     */
    void eventSelected(String eventId);


    /**
     * Init UI state
     */
    void initUIState();

    /**
     * Called when the user clicks "Ändra event".
     */
    void changeEventRequested();


}
