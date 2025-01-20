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

    private DiscoveryTabController() {}

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

        // If user tries to fetch a token for "offline", no real meaning, but let's allow it to do nothing

        if (ConfigurationStore.OFFLINE_EVENT_BOOL.getBooleanValueOrDefault(false)) {
            Popup.INFORMATION.showAndWait("Offline-läge", "Kassan är i offline-läge.");
            ConfigurationStore.OFFLINE_EVENT_BOOL.setBooleanValue(true);
            ConfigurationStore.EVENT_ID_STR.set("offline");
            return;
        }

        try {
            ApiKeyServiceApi apiKeyServiceApi = ApiHelper.INSTANCE.getApiKeyServiceApi();
            GetApiKeyResponse response = apiKeyServiceApi.apiKeyServiceGetApiKey(eventId, cashierCode);

            // If success, store key
            ApiHelper.INSTANCE.setCurrentApiKey(response.getApiKey());
            ConfigurationStore.EVENT_ID_STR.set(eventId);
            ConfigurationStore.API_KEY_STR.set(response.getApiKey());

            // Also fetch and store approved sellers for info:
            ListVendorApplicationsResponse res = ApiHelper.INSTANCE.getVendorApplicationServiceApi()
                    .vendorApplicationServiceListVendorApplications(ConfigurationStore.EVENT_ID_STR.get(), 500, "");
            // Collect approved sellers
            Set<Integer> approvedSellers = new HashSet<>();
            List<VendorApplication> applications = res.getApplications();
            assert applications != null;
            for (VendorApplication application : applications) {
                if ("APPROVED".equalsIgnoreCase(application.getStatus())) {
                    approvedSellers.add(application.getSellerNumber());
                }
            }
            // Convert to JSON
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("approvedSellers", new JSONArray(approvedSellers));
            ConfigurationStore.APPROVED_SELLERS_JSON.set(jsonObject.toString());

            Popup.INFORMATION.showAndWait("Ok!", "Kassan är redo att användas.");
            view.setCashierButtonEnabled(false);
            view.clearCashierCodeField();

        } catch (Exception ex) {
            if (ex instanceof ApiException) {
                Popup.ERROR.showAndWait("Kunde inte hämta token", ex);
            } else {
                Popup.ERROR.showAndWait("Ett fel uppstod", ex);
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

        Event selectedEvent = null;
        // Common setup for both real and offline events
        for (Event event : eventList) {
            if (event.getId().equals(eventId)) {
                view.setEventName(event.getName());
                view.setEventDescription(event.getDescription());
                view.setEventAddress(event.getAddressStreet() + ", " + event.getAddressCity());
                selectedEvent = event;
                break;
            }
        }
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
            view.setEventAddress(selectedEvent.getAddressStreet() + ", "+ selectedEvent.getAddressCity());
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
            for (Event event : eventList) {
                if (event.getId().equals(eventId)) {
                    view.setEventName(event.getName());
                    view.setEventDescription(event.getDescription());
                    view.setEventAddress(event.getAddressStreet() + ", " + event.getAddressCity());
                    view.setCashierButtonEnabled(false);
                    break;
                }
            }
        } else {
            view.setCashierButtonEnabled(true);
        }
    }
}
