package se.goencoder.loppiskassan.controller;

import se.goencoder.loppiskassan.ui.DiscoveryPanelInterface;

import java.util.Set;

public interface DiscoveryControllerInterface {

    // Ties the controller to the UI
    void registerView(DiscoveryPanelInterface view);

    // Called when user clicks "Discover" (with a dateFrom argument)
    void discoverEvents(String dateFrom);

    // Called when user clicks "Get Token" (with the selected eventId and code)
    void fetchApiKey(String eventId, String cashierCode);

    // A dummy method for future usage: retrieving approved sellers for an event
    Set<Integer> getApprovedSellersForEvent(String eventId);
}
