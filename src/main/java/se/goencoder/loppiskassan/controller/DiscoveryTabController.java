package se.goencoder.loppiskassan.controller;


import org.json.JSONArray;
import org.json.JSONObject;
import se.goencoder.iloppis.api.ApiKeyServiceApi;
import se.goencoder.iloppis.api.EventServiceApi;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.*;
import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.records.FileHelper;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.ui.DiscoveryPanelInterface;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.utils.EventUtils;

import java.io.IOException;
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
        EventUtils.populateOfflineEvent(offlineEvent);

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
            eventList.addAll(Objects.requireNonNull(discovered));

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
        Event event = fromId(eventId);

        ConfigurationStore.EVENT_JSON.set(Objects.requireNonNull(event).toJson());

        // Check if the event is offline
        boolean isOffline = "offline".equalsIgnoreCase(eventId);
        RevenueSplit split;
        try {
            split = RevenueSplit.fromJson(ConfigurationStore.REVENUE_SPLIT_JSON.get());
        } catch (IOException e) {
            Popup.ERROR.showAndWait("Kunde inte ladda sparad fördelning", e);
            ConfigurationStore.reset();
            return;
        }
        if (isOffline) {
            // Mark offline in config
            ConfigurationStore.OFFLINE_EVENT_BOOL.setBooleanValue(true);
            ConfigurationStore.EVENT_ID_STR.set("offline");

            // Switch the UI to "active event" mode
            view.setRegisterOpened(true);

            view.showActiveEventInfo(event, split);


            // 6) Possibly let them change again (by default we keep it visible).
            view.setChangeEventButtonVisible(true);
            view.showActiveEventInfo(event, split);

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
                for (VendorApplication application : Objects.requireNonNull(res.getApplications())) {
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
                view.showActiveEventInfo(event, split);

                view.setChangeEventButtonVisible(true);

            } catch (Exception ex) {
                ConfigurationStore.reset();
                if (ex instanceof ApiException) {
                    Popup.ERROR.showAndWait("Kunde inte hämta token", ex);
                } else {
                    Popup.ERROR.showAndWait("Ett fel uppstod", ex);
                }
            }
        }
    }


    @Override
    public void eventSelected(String eventId) {
        view.showDetailForm(true);


        // Common setup for both real and offline events
        Event selectedEvent = fromId(eventId);
        view.setEventName(Objects.requireNonNull(selectedEvent).getName());
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
            RevenueSplit split;
            if (ConfigurationStore.REVENUE_SPLIT_JSON.get() == null) {
                split = new RevenueSplit();
                split.setCharityPercentage(0f);
                split.setMarketOwnerPercentage(85f);
                split.setVendorPercentage(10f);
                split.setPlatformProviderPercentage(5f);
                ConfigurationStore.REVENUE_SPLIT_JSON.set(split.toJson());
            } else {
                try {
                    split = RevenueSplit.fromJson(ConfigurationStore.REVENUE_SPLIT_JSON.get());
                } catch (IOException e) {
                    // should never happen, but if it does, default to 0-0-0
                    Popup.ERROR.showAndWait("Kunde inte ladda sparad fördelning", e);
                    split = new RevenueSplit();
                }
            }
            // Show in the panel
            selectedEvent.setAddressCity("(ingen stad)");
            selectedEvent.setDescription("Ingen beskrivning (offline-läge)");
            selectedEvent.setAddressStreet("(ingen gata)");

            view.setEventName(selectedEvent.getName());
            view.setEventDescription(selectedEvent.getDescription());
            view.setEventAddress(selectedEvent.getAddressStreet() + ", " + selectedEvent.getAddressCity());
            //noinspection DataFlowIssue
            view.setRevenueSplit(
                    split.getMarketOwnerPercentage(),
                    split.getVendorPercentage(),
                    split.getPlatformProviderPercentage());


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

            RevenueSplit revenueSplit = Objects.requireNonNull(market).getRevenueSplit();
            //noinspection DataFlowIssue
            float moSplit = revenueSplit.getMarketOwnerPercentage();
            //noinspection DataFlowIssue
            float vSplit = revenueSplit.getVendorPercentage();
            //noinspection DataFlowIssue
            float pSplit = revenueSplit.getPlatformProviderPercentage();
            view.setRevenueSplit(moSplit, vSplit, pSplit);
            ConfigurationStore.REVENUE_SPLIT_JSON.set(revenueSplit.toJson());
        }
    }


    @Override
    public void initUIState() {


        // If we have a stored event, fill the form with those details.
        String eventId = ConfigurationStore.EVENT_ID_STR.get();
        // if we have event in the list, select the one matching the stored ID
        // Also populate the form with the details
        // And disable the cashier button
        if (eventId != null && !eventId.isEmpty()) {
            try {
                Event event = Event.fromJson(ConfigurationStore.EVENT_JSON.get());
                RevenueSplit split = RevenueSplit.fromJson(ConfigurationStore.REVENUE_SPLIT_JSON.get());
                view.showActiveEventInfo(event, split);
            } catch (IOException e) {
                Popup.ERROR.showAndWait("Kunde inte ladda sparad event", e);
            }
        } else {
            view.setCashierButtonEnabled(true);
        }
    }

    @Override
    public void changeEventRequested() {
        // 1) Show a confirm dialog: "All local records for this register will be thrown away..."
        boolean confirm = Popup.CONFIRM.showConfirmDialog(
                "Byt event?",
                """
                        Alla dina lokala registerposter kommer att raderas.
                        Om du är offline, går de förlorade för alltid.
                        Om du är online, eventuella ej uppladdade poster går förlorade.
                        
                        Vill du fortsätta?"""
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
        try {
            FileHelper.createBackupFile(); // Backup the old data
        } catch (IOException e) {
            Popup.ERROR.showAndWait("Kunde inte rensa kassan", e);
        }
        // Possibly re-enable "Öppna kassa" button etc.
    }

    private Event fromId(String eventId) {
        return EventUtils.findEventById(eventList, eventId);
    }
}
