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
    private List<V1Event> eventList;

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
        V1Event offlineEvent = new V1Event();
        EventUtils.populateOfflineEvent(offlineEvent);

        eventList = new ArrayList<>();
        eventList.add(offlineEvent);

        // Fetch and add real events from iLoppis.
        try {
            EventServiceApi eventApi = ApiHelper.INSTANCE.getEventServiceApi();

            // Create the components separately to avoid serialization issues
            V1EventFilter eventFilter = new V1EventFilter();
            eventFilter.setDateFrom(java.time.OffsetDateTime.parse(dateFrom.contains("T") ? dateFrom : dateFrom + "T00:00:00+00:00"));

            V1Pagination pagination = new V1Pagination();
            pagination.setPageSize(100);

            V1FilterEventsRequest request = new V1FilterEventsRequest();
            request.setFilter(eventFilter);
            request.setPagination(pagination);

            V1FilterEventsResponse response = eventApi.eventServiceFilterEvents(request);
            List<V1Event> discovered = response.getEvents();
            eventList.addAll(Objects.requireNonNull(discovered));

            view.populateEventsTable(eventList);
        } catch (ApiException ex) {
            ex.printStackTrace();
            Popup.ERROR.showAndWait(LocalizationManager.tr("error.fetch_events.title"), ex.getMessage());
            view.populateEventsTable(eventList); // Display only the offline event if an error occurs.
        } catch (Exception ex) {
            Popup.ERROR.showAndWait(LocalizationManager.tr("error.generic.title"), ex.getMessage());
            view.populateEventsTable(eventList);
        }
    }

    @Override
    public void openRegister(String eventId, String cashierCode) {
        if (eventId == null || eventId.isEmpty()) {
            Popup.WARNING.showAndWait(
                    LocalizationManager.tr("error.no_event_selected.title"),
                    LocalizationManager.tr("error.no_event_selected.message"));
            return;
        }
        V1Event event = fromId(eventId);
        ConfigurationStore.EVENT_JSON.set(Objects.requireNonNull(event).toJson());

        // Determine if the event is offline.
        boolean isOffline = "offline".equalsIgnoreCase(eventId);
        V1RevenueSplit split;
        try {
            split = V1RevenueSplit.fromJson(ConfigurationStore.REVENUE_SPLIT_JSON.get());
        } catch (IOException e) {
            Popup.ERROR.showAndWait(LocalizationManager.tr("error.load_saved_split"), e.getMessage());
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

        V1Event selectedEvent = fromId(eventId);
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
                V1Event event = V1Event.fromJson(ConfigurationStore.EVENT_JSON.get());
                V1RevenueSplit split = V1RevenueSplit.fromJson(ConfigurationStore.REVENUE_SPLIT_JSON.get());
                view.showActiveEventInfo(event, split);
            } catch (IOException e) {
                Popup.ERROR.showAndWait(LocalizationManager.tr("error.load_saved_event.title"), e.getMessage());
            }
        } else {
            view.setCashierButtonEnabled(true);
        }
    }

    @Override
    public void changeEventRequested() {
        boolean confirm = Popup.CONFIRM.showConfirmDialog(
                LocalizationManager.tr("confirm.change_event.title"),
                LocalizationManager.tr("confirm.change_event.message"));
        if (!confirm) {
            return;
        }

        ConfigurationStore.reset(); // Reset configuration to clear stored data.

        try {
            FileHelper.createBackupFile(); // Backup current data.
        } catch (IOException e) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("error.clear_register_file", LOPPISKASSAN_CSV),
                    e.getMessage());
        }

        // Reset the UI to the discovery mode.
        view.clearEventsTable();
        view.setRegisterOpened(false);
        view.showDetailForm(false);
        view.setCashierButtonEnabled(true);
    }

    private V1Event fromId(String eventId) {
        // if event list is null or empty, look in Configuration store
        if (eventList == null || eventList.isEmpty()) {
            try {
                V1Event event = V1Event.fromJson(ConfigurationStore.EVENT_JSON.get());
                if (event != null && event.getId().equals(eventId)) {
                    return event;
                }
            } catch (IOException e) {
                Popup.FATAL.showAndWait(
                        LocalizationManager.tr("error.load_saved_event.title"),
                        LocalizationManager.tr("error.load_saved_event.message", CONFIG_FILE_PATH, e.getMessage()));
            }
            return null;
        }

        return EventUtils.findEventById(eventList, eventId);
    }

    private void configureOfflineMode(V1Event event, V1RevenueSplit split) {
        ConfigurationStore.OFFLINE_EVENT_BOOL.setBooleanValue(true);
        ConfigurationStore.EVENT_ID_STR.set("offline");

        view.setRegisterOpened(true);
        view.showActiveEventInfo(event, split);
        view.setChangeEventButtonVisible(true);
    }

    private void configureOnlineMode(String eventId, String cashierCode, V1Event event, V1RevenueSplit split) {
        try {
            ApiKeyServiceApi apiKeyServiceApi = ApiHelper.INSTANCE.getApiKeyServiceApi();
            V1GetApiKeyResponse response = apiKeyServiceApi.apiKeyServiceGetApiKey(eventId, cashierCode, null);

            ApiHelper.INSTANCE.setCurrentApiKey(response.getApiKey());
            ConfigurationStore.EVENT_ID_STR.set(eventId);
            ConfigurationStore.API_KEY_STR.set(response.getApiKey());

            fetchApprovedSellers(eventId);

            Popup.INFORMATION.showAndWait(
                    LocalizationManager.tr("info.register_ready.title"),
                    LocalizationManager.tr("info.register_ready.message"));

            view.setCashierButtonEnabled(false);
            view.clearCashierCodeField();
            view.setRegisterOpened(true);
            view.showActiveEventInfo(event, split);
            view.setChangeEventButtonVisible(true);
        } catch (Exception ex) {
            if (ex instanceof ApiException) {
                Popup.ERROR.showAndWait(
                        LocalizationManager.tr("error.fetch_token.title"),
                        ex);
            } else {
                ConfigurationStore.reset();
                Popup.ERROR.showAndWait(LocalizationManager.tr("error.generic.title"), ex.getMessage());
            }
        }
    }

    private void fetchApprovedSellers(String eventId) throws ApiException {
        V1ListVendorsResponse res = ApiHelper.INSTANCE.getVendorServiceApi()
            .vendorServiceListVendors(eventId, Integer.valueOf(APPROVED_SELLERS_PAGE_SIZE), "", "", null, null, null);
        Set<Integer> approvedSellers = new HashSet<>();
        for (V1Vendor vendor : Objects.requireNonNull(res.getVendors())) {
            if ("APPROVED".equalsIgnoreCase(vendor.getStatus())) {
                approvedSellers.add(vendor.getSellerNumber());
            }
        }
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("approvedSellers", new JSONArray(approvedSellers));
        ConfigurationStore.APPROVED_SELLERS_JSON.set(jsonObject.toString());
    }

    private void handleOfflineEvent(V1Event selectedEvent) {
        view.setRevenueSplitEditable(true);

        V1RevenueSplit split;
        if (ConfigurationStore.REVENUE_SPLIT_JSON.get() == null) {
            split = new V1RevenueSplit();
            split.setCharityPercentage(0f);
            split.setMarketOwnerPercentage(85f);
            split.setVendorPercentage(10f);
            split.setPlatformProviderPercentage(5f);
            ConfigurationStore.REVENUE_SPLIT_JSON.set(split.toJson());
        } else {
            try {
                split = V1RevenueSplit.fromJson(ConfigurationStore.REVENUE_SPLIT_JSON.get());
            } catch (IOException e) {
                Popup.ERROR.showAndWait(LocalizationManager.tr("error.load_saved_split"), e.getMessage());
                split = new V1RevenueSplit();
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

    private void handleOnlineEvent(V1Event selectedEvent) {
        view.setRevenueSplitEditable(false);

        V1Market market = null;
        try {
            V1GetMarketResponse response = ApiHelper.INSTANCE.getApprovedMarketServiceApi()
                    .approvedMarketServiceGetMarket(selectedEvent.getMarketId());
            market = response.getMarket();
        } catch (ApiException e) {
            Popup.WARNING.showAndWait(
                    LocalizationManager.tr("warning.fetch_market_info.title"),
                    LocalizationManager.tr("warning.fetch_market_info.message", e.getMessage()));
        }

        V1RevenueSplit revenueSplit = Objects.requireNonNull(market).getRevenueSplit();
        
        view.setRevenueSplit(
                revenueSplit.getMarketOwnerPercentage(),
                revenueSplit.getVendorPercentage(),
                revenueSplit.getPlatformProviderPercentage());
        ConfigurationStore.REVENUE_SPLIT_JSON.set(revenueSplit.toJson());
    }
}
