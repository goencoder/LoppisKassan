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
import se.goencoder.loppiskassan.localization.LocalizationManager;

import java.io.IOException;
import java.util.*;

import static se.goencoder.loppiskassan.config.ConfigurationStore.CONFIG_FILE_PATH;
import static se.goencoder.loppiskassan.records.FileHelper.LOPPISKASSAN_CSV;

public class DiscoveryTabController implements DiscoveryControllerInterface {

    private static final DiscoveryTabController instance = new DiscoveryTabController();

    // Constant for API requests
    private static final int APPROVED_SELLERS_PAGE_SIZE = 500;

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
        // Clear the events table before populating new data.
        view.clearEventsTable();

        // Add the offline synthetic event.
        Event offlineEvent = new Event();
        EventUtils.populateOfflineEvent(offlineEvent);

        eventList = new ArrayList<>();
        eventList.add(offlineEvent);

        // Fetch and add real events from iLoppis.
        try {
            EventServiceApi eventApi = ApiHelper.INSTANCE.getEventServiceApi();

            // Create the components separately to avoid serialization issues
            EventFilter eventFilter = new EventFilter();
            eventFilter.setDateFrom(dateFrom);

            Pagination pagination = new Pagination();
            pagination.setPageSize(100);

            FilterEventsRequest request = new FilterEventsRequest();
            request.setFilter(eventFilter);
            request.setPagination(pagination);

            FilterEventsResponse response = eventApi.eventServiceFilterEvents(request);
            List<Event> discovered = response.getEvents();
            eventList.addAll(Objects.requireNonNull(discovered));

            view.populateEventsTable(eventList);
        } catch (ApiException ex) {
            ex.printStackTrace();
            Popup.ERROR.showAndWait("Kunde inte hämta event", ex.getMessage());
            view.populateEventsTable(eventList); // Display only the offline event if an error occurs.
        } catch (Exception ex) {
            Popup.ERROR.showAndWait("Ett fel uppstod", ex.getMessage());
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

        // Determine if the event is offline.
        boolean isOffline = "offline".equalsIgnoreCase(eventId);
        RevenueSplit split;
        try {
            split = RevenueSplit.fromJson(ConfigurationStore.REVENUE_SPLIT_JSON.get());
        } catch (IOException e) {
            Popup.ERROR.showAndWait("Kunde inte ladda sparad fördelning", e.getMessage());
            ConfigurationStore.reset();
            return;
        }

        if (isOffline) {
            configureOfflineMode(event, split);
        } else {
            configureOnlineMode(eventId, cashierCode, event, split);
        }
    }

    @Override
    public void eventSelected(String eventId) {
        view.showDetailForm(true);

        Event selectedEvent = fromId(eventId);
        view.setEventName(Objects.requireNonNull(selectedEvent).getName());
        view.setEventDescription(selectedEvent.getDescription());
        view.setEventAddress(selectedEvent.getAddressStreet() + ", " + selectedEvent.getAddressCity());

        boolean isOffline = "offline".equalsIgnoreCase(eventId);
        view.setOfflineMode(isOffline);
        ConfigurationStore.OFFLINE_EVENT_BOOL.setBooleanValue(isOffline);

        if (isOffline) {
            handleOfflineEvent(selectedEvent);
        } else {
            handleOnlineEvent(selectedEvent);
        }
    }

    @Override
    public void initUIState() {
        String eventId = ConfigurationStore.EVENT_ID_STR.get();
        if (eventId != null && !eventId.isEmpty()) {
            try {
                Event event = Event.fromJson(ConfigurationStore.EVENT_JSON.get());
                RevenueSplit split = RevenueSplit.fromJson(ConfigurationStore.REVENUE_SPLIT_JSON.get());
                view.showActiveEventInfo(event, split);
            } catch (IOException e) {
                Popup.ERROR.showAndWait("Kunde inte ladda sparad event", e.getMessage());
            }
        } else {
            view.setCashierButtonEnabled(true);
        }
    }

    @Override
    public void changeEventRequested() {
        boolean confirm = Popup.CONFIRM.showConfirmDialog(
                "Byt event?",
                """
                Alla dina lokala registerposter kommer att raderas.
                Om du är offline, går de förlorade för alltid.
                Om du är online, eventuella ej uppladdade poster går förlorade.

                Vill du fortsätta?
                """
        );
        if (!confirm) {
            return;
        }

        ConfigurationStore.reset(); // Reset configuration to clear stored data.

        try {
            FileHelper.createBackupFile(); // Backup current data.
        } catch (IOException e) {
            Popup.ERROR.showAndWait("Kunde inte rensa kassafil: " + LOPPISKASSAN_CSV, e.getMessage());
        }

        // Reset the UI to the discovery mode.
        view.clearEventsTable();
        view.setRegisterOpened(false);
        view.showDetailForm(false);
        view.setCashierButtonEnabled(true);
    }

    private Event fromId(String eventId) {
        // if event list is null or empty, look in Configuration store
        if (eventList == null || eventList.isEmpty()) {
            try {
                Event event = Event.fromJson(ConfigurationStore.EVENT_JSON.get());
                if (event != null && event.getId().equals(eventId)) {
                    return event;
                }
            } catch (IOException e) {
                Popup.FATAL.showAndWait(
                        "Kunde inte ladda sparad event",
                        "Fel vid laddning av sparad event från " + CONFIG_FILE_PATH +": " + e.getMessage());
            }
            return null;
        }

        return EventUtils.findEventById(eventList, eventId);
    }

    private void configureOfflineMode(Event event, RevenueSplit split) {
        ConfigurationStore.OFFLINE_EVENT_BOOL.setBooleanValue(true);
        ConfigurationStore.EVENT_ID_STR.set("offline");

        view.setRegisterOpened(true);
        view.showActiveEventInfo(event, split);
        view.setChangeEventButtonVisible(true);
    }

    private void configureOnlineMode(String eventId, String cashierCode, Event event, RevenueSplit split) {
        try {
            ApiKeyServiceApi apiKeyServiceApi = ApiHelper.INSTANCE.getApiKeyServiceApi();
            GetApiKeyResponse response = apiKeyServiceApi.apiKeyServiceGetApiKey(eventId, cashierCode);

            ApiHelper.INSTANCE.setCurrentApiKey(response.getApiKey());
            ConfigurationStore.EVENT_ID_STR.set(eventId);
            ConfigurationStore.API_KEY_STR.set(response.getApiKey());

            fetchApprovedSellers(eventId);

            Popup.INFORMATION.showAndWait("Ok!", "Kassan är redo att användas.");

            view.setCashierButtonEnabled(false);
            view.clearCashierCodeField();
            view.setRegisterOpened(true);
            view.showActiveEventInfo(event, split);
            view.setChangeEventButtonVisible(true);
        } catch (Exception ex) {
            ConfigurationStore.reset();
            if (ex instanceof ApiException) {
                Popup.ERROR.showAndWait("Kunde inte hämta token", "Felaktig kassakod?" + ex.getMessage());
            } else {
                Popup.ERROR.showAndWait("Ett fel uppstod", ex.getMessage());
            }
        }
    }

    private void fetchApprovedSellers(String eventId) throws ApiException {
        ListVendorsResponse res = ApiHelper.INSTANCE.getVendorServiceApi()
            .vendorServiceListVendors(eventId, Integer.valueOf(APPROVED_SELLERS_PAGE_SIZE), "", "");
        Set<Integer> approvedSellers = new HashSet<>();
        for (Vendor vendor : Objects.requireNonNull(res.getVendors())) {
            if ("APPROVED".equalsIgnoreCase(vendor.getStatus())) {
                approvedSellers.add(vendor.getSellerNumber());
            }
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("approvedSellers", new JSONArray(approvedSellers));
        ConfigurationStore.APPROVED_SELLERS_JSON.set(jsonObject.toString());
    }

    private void handleOfflineEvent(Event selectedEvent) {
        view.setRevenueSplitEditable(true);

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
                Popup.ERROR.showAndWait(LocalizationManager.tr("error.load_saved_split"), e.getMessage());
                split = new RevenueSplit();
            }
        }

        selectedEvent.setAddressCity(LocalizationManager.tr("event.no_city"));
        selectedEvent.setDescription(LocalizationManager.tr("event.no_description_offline"));
        selectedEvent.setAddressStreet(LocalizationManager.tr("event.no_street"));

        view.setEventName(selectedEvent.getName());
        view.setEventDescription(selectedEvent.getDescription());
        view.setEventAddress(selectedEvent.getAddressStreet() + ", " + selectedEvent.getAddressCity());

        view.setRevenueSplit(
                split.getMarketOwnerPercentage(),
                split.getVendorPercentage(),
                split.getPlatformProviderPercentage());
    }

    private void handleOnlineEvent(Event selectedEvent) {
        view.setRevenueSplitEditable(false);

        Market market = null;
        try {
            GetMarketResponse response = ApiHelper.INSTANCE.getApprovedMarketServiceApi()
                    .approvedMarketServiceGetMarket(selectedEvent.getMarketId());
            market = response.getMarket();
        } catch (ApiException e) {
            Popup.WARNING.showAndWait(
                    LocalizationManager.tr("warning.fetch_market_info.title"),
                    LocalizationManager.tr("warning.fetch_market_info.message", e.getMessage()));
        }

        RevenueSplit revenueSplit = Objects.requireNonNull(market).getRevenueSplit();
        view.setRevenueSplit(
                revenueSplit.getMarketOwnerPercentage(),
                revenueSplit.getVendorPercentage(),
                revenueSplit.getPlatformProviderPercentage());
        ConfigurationStore.REVENUE_SPLIT_JSON.set(revenueSplit.toJson());
    }
}
