package se.goencoder.loppiskassan.controller;

import org.json.JSONArray;
import org.json.JSONObject;
import se.goencoder.iloppis.api.ApiKeyServiceApi;
import se.goencoder.iloppis.api.EventServiceApi;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.*;
import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.model.BulkUploadResult;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.ui.DiscoveryPanelInterface;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.ui.dialogs.BulkUploadDialog;
import se.goencoder.loppiskassan.utils.EventUtils;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.storage.LocalEvent;
import se.goencoder.loppiskassan.storage.LocalEventRepository;

import java.awt.Frame;
import javax.swing.SwingUtilities;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.JOptionPane;

import static se.goencoder.loppiskassan.config.ConfigurationStore.CONFIG_FILE_PATH;

public class DiscoveryTabController implements DiscoveryControllerInterface {

    private static final DiscoveryTabController instance = new DiscoveryTabController();

    // Constant for API requests
    private static final int APPROVED_SELLERS_PAGE_SIZE = 500;

    private DiscoveryPanelInterface view;
    private volatile List<V1Event> eventList;
    private volatile Map<String, LocalEvent> localEventMap = new HashMap<>();
    private ScheduledExecutorService refreshScheduler;
    private String lastDateFrom;

    private DiscoveryTabController() {
    }

    public static DiscoveryTabController getInstance() {
        return instance;
    }

    @Override
    public void registerView(DiscoveryPanelInterface view) {
        this.view = view;
    }

    private void loadAllEvents() {
        String dateFrom = lastDateFrom;
        if (dateFrom == null || dateFrom.isBlank()) {
            dateFrom = LocalDate.now().toString();
        }

        se.goencoder.loppiskassan.ui.EDT.run(view::clearEventsTable);

        // Build lists in local variables to avoid exposing a half-built list
        // to the EDT (eventSelected reads eventList/localEventMap).
        List<V1Event> newEventList = new ArrayList<>();
        Map<String, LocalEvent> newLocalEventMap = new HashMap<>();

        // Load local events from disk
        try {
            List<LocalEvent> localEvents = LocalEventRepository.loadAll();
            for (LocalEvent localEvent : localEvents) {
                V1Event event = toLocalV1Event(localEvent);
                newEventList.add(event);
                newLocalEventMap.put(localEvent.getEventId(), localEvent);
            }
        } catch (IOException e) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("error.load_local_events.title"),
                    LocalizationManager.tr("error.load_local_events.message", e.getMessage()));
        }

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
            newEventList.addAll(Objects.requireNonNull(discovered));
        } catch (ApiException ex) {
            Popup.ERROR.showAndWait(LocalizationManager.tr("error.fetch_events.title"), ex.getMessage());
        } catch (Exception ex) {
            Popup.ERROR.showAndWait(LocalizationManager.tr("error.generic.title"), ex.getMessage());
        }

        // Swap atomically — volatile write ensures visibility to EDT
        eventList = newEventList;
        localEventMap = newLocalEventMap;

        se.goencoder.loppiskassan.ui.EDT.run(() -> view.populateEventsTable(eventList));
    }

    private void startAutoRefresh() {
        if (refreshScheduler != null) {
            return;
        }
        refreshScheduler = Executors.newSingleThreadScheduledExecutor();
        loadAllEvents();
        refreshScheduler.scheduleAtFixedRate(
                this::loadAllEvents,
                60_000,
                60_000,
                TimeUnit.MILLISECONDS
        );
    }

    private V1Event toLocalV1Event(LocalEvent localEvent) {
        V1Event event = new V1Event();
        event.setId(localEvent.getEventId());
        event.setName(localEvent.getName());
        event.setDescription(localEvent.getDescription());
        String street = localEvent.getAddressStreet();
        String city = localEvent.getAddressCity();
        event.setAddressStreet(street == null || street.isBlank()
                ? LocalizationManager.tr("event.no_street")
                : street);
        event.setAddressCity(city == null || city.isBlank()
                ? LocalizationManager.tr("event.no_city")
                : city);
        event.setStartTime(localEvent.getCreatedAt());
        event.setEndTime(null);
        return event;
    }

    @Override
    public void discoverEvents(String dateFrom) {
        lastDateFrom = dateFrom;
        loadAllEvents();
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
        if (event == null) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("error.no_event_selected.title"),
                    LocalizationManager.tr("error.no_event_selected.message"));
            return;
        }
        ConfigurationStore.EVENT_JSON.set(event.toJson());

        // Determine if the event is local.
        boolean isLocal = localEventMap.containsKey(eventId);
        V1RevenueSplit split;
        if (isLocal) {
            LocalEvent localEvent = localEventMap.get(eventId);
            split = localEvent == null ? null : localEvent.getRevenueSplit();
            if (split == null) {
                split = new V1RevenueSplit();
                split.setCharityPercentage(0f);
                split.setMarketOwnerPercentage(10f);
                split.setVendorPercentage(85f);
                split.setPlatformProviderPercentage(5f);
            }
            ConfigurationStore.REVENUE_SPLIT_JSON.set(split.toJson());
            configureLocalMode(eventId, split);
        } else {
            try {
                split = V1RevenueSplit.fromJson(ConfigurationStore.REVENUE_SPLIT_JSON.get());
            } catch (IOException e) {
                Popup.ERROR.showAndWait(LocalizationManager.tr("error.load_saved_split"), e.getMessage());
                ConfigurationStore.reset();
                return;
            }
            configureOnlineMode(eventId, cashierCode, event, split);
        }
    }

    @Override
    public void eventSelected(String eventId) {
        view.showDetailForm(true);

        // Determine local mode from the event ID pattern so we can set it
        // before the fromId lookup — this ensures cashier code visibility
        // is correct even if the event lookup fails (e.g. during auto-refresh).
        boolean isLocal = eventId != null && eventId.startsWith("local-");
        view.setLocalMode(isLocal);
        ConfigurationStore.LOCAL_EVENT_BOOL.setBooleanValue(isLocal);

        V1Event selectedEvent = fromId(eventId);
        if (selectedEvent == null) {
            return;
        }

        view.setEventName(selectedEvent.getName());
        view.setEventDescription(selectedEvent.getDescription());
        String address = selectedEvent.getAddressStreet() + ", " + selectedEvent.getAddressCity();
        view.setEventAddress(address.replaceFirst("^,\\s*", ""));

        if (isLocal) {
            LocalEvent localEvent = localEventMap.get(eventId);
            handleLocalEvent(localEvent);
        } else {
            handleOnlineEvent(selectedEvent);
        }
    }

    @Override
    public void initUIState() {
        loadAllEvents();
        String eventId = ConfigurationStore.EVENT_ID_STR.get();
        if (eventId != null && !eventId.isEmpty()
                && !"offline".equalsIgnoreCase(eventId)
                && !"local".equalsIgnoreCase(eventId)) {
            try {
                V1Event event = fromId(eventId);
                if (event == null) {
                    ConfigurationStore.reset();
                    view.setRegisterOpened(false);
                    view.setCashierButtonEnabled(true);
                } else {
                    V1RevenueSplit split = V1RevenueSplit.fromJson(ConfigurationStore.REVENUE_SPLIT_JSON.get());
                    view.setLocalMode(localEventMap.containsKey(eventId));
                    ConfigurationStore.LOCAL_EVENT_BOOL.setBooleanValue(localEventMap.containsKey(eventId));
                    view.showActiveEventInfo(event, split);
                }
            } catch (IOException e) {
                Popup.ERROR.showAndWait(LocalizationManager.tr("error.load_saved_event.title"), e.getMessage());
            }
        } else {
            view.setCashierButtonEnabled(true);
        }

        startAutoRefresh();
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

        // Reset the UI to the discovery mode.
        view.clearEventsTable();
        view.setRegisterOpened(false);
        view.showDetailForm(false);
        view.setCashierButtonEnabled(true);

        loadAllEvents();
    }

    @Override
    public void saveLocalEventEdits(String eventId, String name, String description, String address,
                                    float marketOwner, float vendor, float platform) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        LocalEvent existing = localEventMap.get(eventId);
        if (existing == null) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("local_event.save_failed.title"),
                    LocalizationManager.tr("local_event.save_failed.message", eventId));
            return;
        }

        if (name == null || name.isBlank()) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("local_event.error.title"),
                    LocalizationManager.tr("local_event.error.name_required"));
            return;
        }

        float sum = marketOwner + vendor + platform;
        if (Math.abs(sum - 100f) > 0.01f) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("local_event.error.title"),
                    LocalizationManager.tr("local_event.error.invalid_split"));
            return;
        }

        String[] addressParts = splitAddress(address);
        String street = addressParts[0];
        String city = addressParts[1];

        V1RevenueSplit split = new V1RevenueSplit();
        split.setCharityPercentage(0f);
        split.setMarketOwnerPercentage(marketOwner);
        split.setVendorPercentage(vendor);
        split.setPlatformProviderPercentage(platform);

        LocalEvent updated = new LocalEvent(
                existing.getEventId(),
                existing.getEventType(),
                name,
                description,
                street,
                city,
                existing.getCreatedAt(),
                split
        );

        try {
            LocalEventRepository.save(updated);
            localEventMap.put(eventId, updated);
            loadAllEvents();
            view.selectEventById(eventId);
            Popup.INFORMATION.showAndWait(
                    LocalizationManager.tr("local_event.save_success.title"),
                    LocalizationManager.tr("local_event.save_success.message"));
        } catch (IOException e) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("local_event.save_failed.title"),
                    LocalizationManager.tr("local_event.save_failed.message", e.getMessage()));
        }
    }

    private String[] splitAddress(String address) {
        if (address == null || address.isBlank()) {
            return new String[]{"", ""};
        }
        String[] parts = address.split(",", 2);
        String street = parts[0].trim();
        String city = parts.length > 1 ? parts[1].trim() : "";
        return new String[]{street, city};
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

    private void configureLocalMode(String eventId, V1RevenueSplit split) {
        ConfigurationStore.LOCAL_EVENT_BOOL.setBooleanValue(true);
        ConfigurationStore.EVENT_ID_STR.set(eventId);

        V1Event localEvent = fromId(eventId);
        if (localEvent == null) {
            localEvent = new V1Event();
            localEvent.setId(eventId);
            localEvent.setName(eventId);
        }

        view.setRegisterOpened(true);
        view.showActiveEventInfo(localEvent, split);
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

    private void handleLocalEvent(LocalEvent localEvent) {
        if (localEvent == null) {
            return;
        }
        view.setRevenueSplitEditable(true);

        V1RevenueSplit split = localEvent.getRevenueSplit();
        if (split == null) {
            split = new V1RevenueSplit();
            split.setCharityPercentage(0f);
            split.setMarketOwnerPercentage(10f);
            split.setVendorPercentage(85f);
            split.setPlatformProviderPercentage(5f);
        }

        ConfigurationStore.REVENUE_SPLIT_JSON.set(split.toJson());

        String description = localEvent.getDescription();
        if (description == null || description.isBlank()) {
            description = LocalizationManager.tr("event.no_description_local");
        }
        view.setEventName(localEvent.getName());
        view.setEventDescription(description);
        String street = localEvent.getAddressStreet();
        String city = localEvent.getAddressCity();
        if (street == null || street.isBlank()) {
            street = LocalizationManager.tr("event.no_street");
        }
        if (city == null || city.isBlank()) {
            city = LocalizationManager.tr("event.no_city");
        }
        view.setEventAddress(street + ", " + city);

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

    @Override
    public void uploadLocalEventRequested(String eventId) {
        try {
            LocalEvent localEvent = LocalEventRepository.load(eventId);
            if (localEvent == null) {
                Popup.WARNING.showAndWait(
                    LocalizationManager.tr("error"),
                    "Local event not found: " + eventId);
                return;
            }

            // Find the Frame parent for the dialog
            Frame parentFrame = null;
            for (Frame frame : Frame.getFrames()) {
                if (frame.isDisplayable() && frame.getName().equals("MainFrame")) {
                    parentFrame = frame;
                    break;
                }
            }
            if (parentFrame == null) {
                parentFrame = Frame.getFrames().length > 0 ? Frame.getFrames()[0] : null;
            }

            // Show upload dialog
            BulkUploadDialog dialog = new BulkUploadDialog(parentFrame, localEvent);
            BulkUploadResult result = dialog.showDialog();

            if (result != null && result.hasResults()) {
                if (result.isFullSuccess()) {
                    JOptionPane.showMessageDialog(
                        parentFrame,
                        String.format("✅ %d items uploaded successfully", 
                            result.acceptedItems.size()),
                        LocalizationManager.tr("bulk_upload.success"),
                        JOptionPane.INFORMATION_MESSAGE);
                } else if (result.isPartialSuccess()) {
                    JOptionPane.showMessageDialog(
                        parentFrame,
                        result.getSummaryText(),
                        LocalizationManager.tr("bulk_upload.summary"),
                        JOptionPane.WARNING_MESSAGE);
                } else if (!result.errorMessages.isEmpty()) {
                    JOptionPane.showMessageDialog(
                        parentFrame,
                        String.join("\n", result.errorMessages),
                        LocalizationManager.tr("error"),
                        JOptionPane.ERROR_MESSAGE);
                }
            }
        } catch (IOException e) {
            Popup.ERROR.showAndWait(
                LocalizationManager.tr("error"),
                "Failed to load local event: " + e.getMessage());
        }
    }
}
