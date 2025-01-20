package se.goencoder.loppiskassan.controller;


import org.json.JSONArray;
import org.json.JSONObject;
import se.goencoder.iloppis.api.ApiKeyServiceApi;
import se.goencoder.iloppis.api.EventServiceApi;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.*;
import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.ui.DiscoveryPanelInterface;
import se.goencoder.loppiskassan.ui.Popup;

import java.util.*;

public class DiscoveryTabController implements DiscoveryControllerInterface {

    private static final DiscoveryTabController instance = new DiscoveryTabController();
    private DiscoveryPanelInterface view;
    private List<Event> eventList;

    private DiscoveryTabController() {
    }

    public static DiscoveryTabController getInstance() {
        return instance;
    }

    @Override
    public void registerView(DiscoveryPanelInterface view) {
        this.view = view;
    }

    @Override
    public void discoverEvents(String dateFrom) {
        // Clear current table
        view.clearEventsTable();

        // Add the "offline" synthetic event at the top
        Event offlineEvent = new Event();
        offlineEvent.setId("offline");
        offlineEvent.setName("Offline-loppis");
        offlineEvent.setAddressCity("");   // no city
        offlineEvent.setStartDate(null);   // no date
        offlineEvent.setEndDate(null);     // no date

        eventList = new ArrayList<>();
        eventList.add(offlineEvent);

        // Then also fetch real events from iLoppis
        try {
            EventServiceApi eventApi = ApiHelper.INSTANCE.getEventServiceApi();
            FilterEventsRequest request = new FilterEventsRequest()
                    .filter(new EventFilter().dateFrom(dateFrom))
                    .pagination(new Pagination().pageSize(100));

            FilterEventsResponse response = eventApi.eventServiceFilterEvents(request);
            List<Event> discovered = response.getEvents();
            eventList.addAll(discovered);

            view.populateEventsTable(eventList);
        } catch (ApiException ex) {
            Popup.ERROR.showAndWait("Kunde inte hämta event", ex);
            // Still populate with just the offline
            view.populateEventsTable(eventList);
        } catch (Exception ex) {
            Popup.ERROR.showAndWait("Ett fel uppstod", ex);
            view.populateEventsTable(eventList);
        }
    }

    @Override
    public void openRegister(String eventId, String cashierCode) {
        if (eventId == null || eventId.isEmpty()) {
            Popup.WARNING.showAndWait("Ingen rad vald", "Du måste välja ett event först.");
            return;
        }

        // Check if the event is offline
        boolean isOffline = "offline".equalsIgnoreCase(eventId);

        if (isOffline) {
            // 1) Mark offline in config
            ConfigurationStore.OFFLINE_EVENT_BOOL.setBooleanValue(true);
            ConfigurationStore.EVENT_ID_STR.set("offline");

            // 2) Show a small info popup (optional)
            Popup.INFORMATION.showAndWait("Offline-läge", "Kassan är i offline-läge.");

            // 3) Switch the UI to "active event" mode
            view.setRegisterOpened(true);

            // 4) Find the "event" in our eventList if you want to display name/desc
            //    (In your code, you may have a special offline object.)
            Event event = fromId("offline");
            // If it doesn't exist, build it
            if (event == null) {
                event = new Event();
                event.setId("offline");
                event.setName("Offline-loppis");
                event.setDescription("Ingen beskrivning (offline-läge)");
                event.setAddressStreet("(ingen gata)");
                event.setAddressCity("(ingen stad)");
            }
            // 5) Show it in the active-event panel
            view.showActiveEventInfo(
                    event.getName(),
                    event.getDescription(),
                    event.getAddressStreet() + ", " + event.getAddressCity()
            );
            // 6) Possibly let them change again (by default we keep it visible).
            view.setChangeEventButtonVisible(true);

        } else {
            // -------------- ONLINE --------------
            try {
                // 1) Call the API to get the cashier token
                ApiKeyServiceApi apiKeyServiceApi = ApiHelper.INSTANCE.getApiKeyServiceApi();
                GetApiKeyResponse response = apiKeyServiceApi.apiKeyServiceGetApiKey(eventId, cashierCode);

                // 2) If success, store key
                ApiHelper.INSTANCE.setCurrentApiKey(response.getApiKey());
                ConfigurationStore.EVENT_ID_STR.set(eventId);
                ConfigurationStore.API_KEY_STR.set(response.getApiKey());

                // 3) Also fetch and store approved sellers for info:
                ListVendorApplicationsResponse res = ApiHelper.INSTANCE.getVendorApplicationServiceApi()
                        .vendorApplicationServiceListVendorApplications(eventId, 500, "");
                Set<Integer> approvedSellers = new HashSet<>();
                for (VendorApplication application : res.getApplications()) {
                    if ("APPROVED".equalsIgnoreCase(application.getStatus())) {
                        approvedSellers.add(application.getSellerNumber());
                    }
                }
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("approvedSellers", new JSONArray(approvedSellers));
                ConfigurationStore.APPROVED_SELLERS_JSON.set(jsonObject.toString());

                // 4) Show success
                Popup.INFORMATION.showAndWait("Ok!", "Kassan är redo att användas.");

                // 5) Switch the UI to "active event" mode
                view.setCashierButtonEnabled(false);
                view.clearCashierCodeField();
                view.setRegisterOpened(true);

                // 6) Show the event's info
                Event event = fromId(eventId);
                view.showActiveEventInfo(
                        event.getName(),
                        event.getDescription(),
                        event.getAddressStreet() + ", " + event.getAddressCity()
                );
                view.setChangeEventButtonVisible(true);

            } catch (Exception ex) {
                if (ex instanceof ApiException) {
                    Popup.ERROR.showAndWait("Kunde inte hämta token", ex);
                } else {
                    Popup.ERROR.showAndWait("Ett fel uppstod", ex);
                }
            }
        }
    }

    @Override
    public Set<Integer> getApprovedSellersForEvent(String eventId) {
        // For now, a dummy example. Potentially parse from config or call an endpoint
        return new HashSet<>(Collections.singletonList(1));
    }

    @Override
    public void eventSelected(String eventId) {
        view.showDetailForm(true);


        // Common setup for both real and offline events
        Event selectedEvent = fromId(eventId);
        view.setEventName(selectedEvent.getName());
        view.setEventDescription(selectedEvent.getDescription());
        view.setEventAddress(selectedEvent.getAddressStreet() + ", " + selectedEvent.getAddressCity());

        boolean isOffline = "offline".equalsIgnoreCase(eventId);
        view.setOfflineMode(isOffline);
        ConfigurationStore.OFFLINE_EVENT_BOOL.setBooleanValue(isOffline);
        if (isOffline) {
            // Mark offline in config

            // Let the UI know we are in offline mode
            view.setRevenueSplitEditable(true);

            // Any existing stored splits? If not, default to 0-0-0
            int moSplit = ConfigurationStore.REVENUE_SPLIT_MARKET_FLOAT.getIntValueOrDefault(10);
            int vSplit = ConfigurationStore.REVENUE_SPLIT_VENDOR_FLOAT.getIntValueOrDefault(85);
            int pSplit = ConfigurationStore.REVENUE_SPLIT_PLATFORM_FLOAT.getIntValueOrDefault(5);

            // Show in the panel
            selectedEvent.setAddressCity("(ingen stad)");
            selectedEvent.setDescription("Ingen beskrivning (offline-läge)");
            selectedEvent.setAddressStreet("(ingen gata)");

            view.setEventName(selectedEvent.getName());
            view.setEventDescription(selectedEvent.getDescription());
            view.setEventAddress(selectedEvent.getAddressStreet() + ", " + selectedEvent.getAddressCity());
            view.setRevenueSplit(moSplit, vSplit, pSplit);


        } else {
            view.setRevenueSplitEditable(false);
            // Get the revenue split from the API
            Market market = null;
            try {
                GetMarketResponse response = ApiHelper.INSTANCE.getApprovedMarketServiceApi().approvedMarketServiceGetMarket(selectedEvent.getMarketId());
                market = response.getMarket();
            } catch (ApiException e) {
                Popup.WARNING.showAndWait("Kunde inte hämta marknadsinfo", e);
            }

            RevenueSplit revenueSplit = market.getRevenueSplit();
            float moSplit = revenueSplit.getMarketOwnerPercentage();
            float vSplit = revenueSplit.getVendorPercentage();
            float pSplit = revenueSplit.getPlatformProviderPercentage();
            view.setRevenueSplit(moSplit, vSplit, pSplit);

            // Store them in config as well, if you like:
            ConfigurationStore.REVENUE_SPLIT_MARKET_FLOAT.setFloatValue(moSplit);
            ConfigurationStore.REVENUE_SPLIT_VENDOR_FLOAT.setFloatValue(vSplit);
            ConfigurationStore.REVENUE_SPLIT_PLATFORM_FLOAT.setFloatValue(pSplit);
        }
    }

    @Override
    public void setRevenueSplitFromUI(float marketOwner, float vendor, float platform) {
        // Save to config
        ConfigurationStore.REVENUE_SPLIT_MARKET_FLOAT.setFloatValue(marketOwner);
        ConfigurationStore.REVENUE_SPLIT_VENDOR_FLOAT.setFloatValue(vendor);
        ConfigurationStore.REVENUE_SPLIT_PLATFORM_FLOAT.setFloatValue(platform);
    }

    @Override
    public void initUIState() {
        // If we have a stored event, fill the form with those details.
        String eventId = ConfigurationStore.EVENT_ID_STR.get();
        // if we have event in the list, select the one matching the stored ID
        // Also populate the form with the details
        // And disable the cashier button
        if (eventId != null && !eventId.isEmpty()) {
            Event event = fromId(eventId);
            view.setEventName(event.getName());
            view.setEventDescription(event.getDescription());
            view.setEventAddress(event.getAddressStreet() + ", " + event.getAddressCity());
            view.setCashierButtonEnabled(false);
        } else {
            view.setCashierButtonEnabled(true);
        }
    }

    @Override
    public void changeEventRequested() {
        // 1) Show a confirm dialog: "All local records for this register will be thrown away..."
        boolean confirm = Popup.CONFIRM.showConfirmDialog(
                "Byt event?",
                "Alla dina lokala registerposter kommer att raderas.\n" +
                        "Om du är offline, går de förlorade för alltid.\n" +
                        "Om du är online, eventuella ej uppladdade poster går förlorade.\n\n" +
                        "Vill du fortsätta?"
        );
        if (!confirm) {
            return;
        }

        // 2) Clear local data. For example, we can do:
        //    - Reset configuration store
        //    - Possibly ask HistoryTabController or CashierTabController to erase everything
        //    - Then revert the UI to the normal "discoveryMode"

        // Reset the config
        ConfigurationStore.reset();


        // Potentially we also have the HistoryTabController clear local CSV? Or user must do that?
        // Up to you. Possibly:
        // HistoryTabController.getInstance().clearAllDataButWeNeedToAddMethod();

        // 3) Update the UI back to normal
        view.clearEventsTable();      // Clears table
        view.setRegisterOpened(false);// Switch card back to discovery
        view.showDetailForm(false);   // If we want the “no selection” detail
        view.setCashierButtonEnabled(true);
        // Possibly re-enable "Öppna kassa" button etc.
    }

    private Event fromId(String eventId) {
        for (Event event : eventList) {
            if (event.getId().equals(eventId)) {
                return event;
            }
        }
        return null;
    }
}
