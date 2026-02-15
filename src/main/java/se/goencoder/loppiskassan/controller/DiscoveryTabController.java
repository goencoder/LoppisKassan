package se.goencoder.loppiskassan.controller;

import org.json.JSONArray;
import org.json.JSONObject;
import se.goencoder.iloppis.api.ApiKeyServiceApi;
import se.goencoder.iloppis.api.EventServiceApi;
import se.goencoder.iloppis.api.VendorServiceApi;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.*;
import se.goencoder.loppiskassan.config.AppMode;
import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.config.LocalConfigurationStore;
import se.goencoder.loppiskassan.config.ILoppisConfigurationStore;
import se.goencoder.loppiskassan.model.BulkUploadResult;
import se.goencoder.loppiskassan.model.discovery.DiscoveryState;
import se.goencoder.loppiskassan.rest.AuthErrorHandler;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.ui.DiscoveryPanelInterface;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.ui.dialogs.CashierCodeDialog;
import se.goencoder.loppiskassan.util.AppPaths;
import se.goencoder.loppiskassan.utils.EventUtils;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.storage.LocalEvent;
import se.goencoder.loppiskassan.storage.LocalEventRepository;
import se.goencoder.loppiskassan.storage.LocalEventType;

import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static se.goencoder.loppiskassan.config.GlobalConfigurationStore.*;

public class DiscoveryTabController implements DiscoveryControllerInterface {

    private static final Logger log = Logger.getLogger(DiscoveryTabController.class.getName());
    private static final DiscoveryTabController instance = new DiscoveryTabController();

    // Constant for API requests
    private static final int APPROVED_SELLERS_PAGE_SIZE = 500;

    private DiscoveryPanelInterface view;
    private volatile List<V1Event> eventList;
    private volatile Map<String, LocalEvent> localEventMap = new HashMap<>();
    private final DiscoveryState state = new DiscoveryState();
    private ScheduledExecutorService refreshScheduler;
    private String lastDateFrom;

    private DiscoveryTabController() {
    }

    public static DiscoveryTabController getInstance() {
        return instance;
    }

/**
     * Get the current discovery state for observers.
     * Views can register PropertyChangeListeners on this state to react to changes.
     *
     * @return the discovery state
     */
    public DiscoveryState getState() {
        return state;
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

        // Load local events from disk (always available)
        try {
            List<LocalEvent> localEvents = LocalEventRepository.loadAll();
            for (LocalEvent localEvent : localEvents) {
                // Only include LOCAL events here, not cached ONLINE events
                if (localEvent.getEventType() == LocalEventType.LOCAL) {
                    V1Event event = toLocalV1Event(localEvent);
                    newEventList.add(event);
                    newLocalEventMap.put(localEvent.getEventId(), localEvent);
                }
            }
        } catch (IOException e) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("error.load_local_events.title"),
                    LocalizationManager.tr("error.load_local_events.message", e.getMessage()));
        }

        // Check connectivity before attempting API call
        boolean online = se.goencoder.loppiskassan.rest.ConnectivityChecker.isOnline();
        state.setOfflineMode(!online);

        if (online) {
            // Fetch fresh events from iLoppis API
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
                
                // Refresh existing caches with fresh data
                se.goencoder.loppiskassan.storage.OnlineEventCache.refreshCaches(discovered);
                
            } catch (ApiException ex) {
                // API error despite connectivity check - fall back to cache
                loadCachedOnlineEvents(newEventList);
                state.setOfflineMode(true);
                // Don't show error dialog - just silently use cache
            } catch (Exception ex) {
                Popup.ERROR.showAndWait(LocalizationManager.tr("error.generic.title"), ex.getMessage());
            }
        } else {
            // Offline: load cached iLoppis events
            loadCachedOnlineEvents(newEventList);
        }

        // Swap atomically — volatile write ensures visibility to EDT
        eventList = newEventList;
        localEventMap = newLocalEventMap;

        // Update state with the loaded events
        state.setEvents(newEventList);
        state.setDateFrom(dateFrom);

        se.goencoder.loppiskassan.ui.EDT.run(() -> view.populateEventsTable(eventList));
    }

    private void loadCachedOnlineEvents(List<V1Event> targetList) {
        List<se.goencoder.loppiskassan.storage.CachedOnlineEvent> cached = 
                se.goencoder.loppiskassan.storage.OnlineEventCache.loadCachedEvents();
        for (se.goencoder.loppiskassan.storage.CachedOnlineEvent c : cached) {
            V1Event event = c.toV1Event();
            targetList.add(event);
        }
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
    public void openRegister(String eventId) {
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
        
        // Determine if the event is local.
        boolean isLocal = localEventMap.containsKey(eventId);
        
        // Store event data in appropriate config store
        if (isLocal) {
            LocalConfigurationStore.setEventData(event.toJson());
        } else {
            ILoppisConfigurationStore.setEventData(event.toJson());
        }

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
            LocalConfigurationStore.setRevenueSplit(split.toJson());
            configureLocalMode(eventId, split);
        } else {
            try {
                split = V1RevenueSplit.fromJson(ILoppisConfigurationStore.getRevenueSplit());
            } catch (IOException e) {
                Popup.ERROR.showAndWait(LocalizationManager.tr("error.load_saved_split"), e.getMessage());
                LocalConfigurationStore.reset();
                ILoppisConfigurationStore.reset();
                return;
            }
            configureOnlineMode(eventId, event, split);
        }
    }

    @Override
    public void eventSelected(String eventId) {
        view.showDetailForm(true);

        // Determine local mode from the event ID pattern so we can set it
        // before the fromId lookup — this ensures credential visibility
        // is correct even if the event lookup fails (e.g. during auto-refresh).
        boolean isLocal = eventId != null && eventId.startsWith("local-");
        view.setLocalMode(isLocal);
        AppModeManager.setMode(isLocal ? AppMode.LOCAL : AppMode.ILOPPIS);
        
        // Update state
        state.setLocalMode(isLocal);
        state.setDetailFormVisible(true);

        V1Event selectedEvent = fromId(eventId);
        if (selectedEvent == null) {
            return;
        }

        // Update state with selected event
        state.setSelectedEvent(selectedEvent);

        view.setEventName(selectedEvent.getName());
        view.setEventDescription(selectedEvent.getDescription());
        String address = selectedEvent.getAddressStreet() + ", " + selectedEvent.getAddressCity();
        view.setEventAddress(address.replaceFirst("^,\\s*", ""));

        if (isLocal) {
            LocalEvent localEvent = localEventMap.get(eventId);
            handleLocalEvent(localEvent);
            view.setCachedCredentialsStatus(false);
        } else {
            handleOnlineEvent(selectedEvent);
            view.setCachedCredentialsStatus(
                    se.goencoder.loppiskassan.storage.OnlineEventCache.hasCachedCredentials(eventId));
        }
    }

    @Override
    public void initUIState() {
        loadAllEvents();
        String eventId = AppModeManager.getEventId();
        if (eventId != null && !eventId.isEmpty()
                && !"offline".equalsIgnoreCase(eventId)
                && !"local".equalsIgnoreCase(eventId)) {
            try {
                V1Event event = fromId(eventId);
                if (event == null) {
                    LocalConfigurationStore.reset();
                    ILoppisConfigurationStore.reset();
                    view.setRegisterOpened(false);
                    view.setCashierButtonEnabled(true);
                } else {
                    V1RevenueSplit split = V1RevenueSplit.fromJson(LocalConfigurationStore.getRevenueSplit());
                    view.setLocalMode(localEventMap.containsKey(eventId));
                    AppModeManager.setMode(localEventMap.containsKey(eventId) ? AppMode.LOCAL : AppMode.ILOPPIS);
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

        LocalConfigurationStore.reset();
        ILoppisConfigurationStore.reset();

        // Reset the UI to the discovery mode.
        view.clearEventsTable();
        view.setRegisterOpened(false);
        view.showDetailForm(false);
        view.setCashierButtonEnabled(true);

        // Reset state
        state.setRegisterOpened(false);
        state.setSelectedEvent(null);
        state.setRevenueSplit(null);
        state.setDetailFormVisible(false);
        state.setCashierButtonEnabled(true);

        loadAllEvents();
    }

    @Override
    public void forgetCashierCode(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }

        se.goencoder.loppiskassan.storage.OnlineEventCache.clearCachedCredentials(eventId);

        if (eventId.equals(ILoppisConfigurationStore.getEventId())) {
            ILoppisConfigurationStore.setApiKey(null);
            ApiHelper.INSTANCE.clearCurrentApiKey();
        }

        view.setCachedCredentialsStatus(false);
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

    @Override
    public void deleteLocalEvent(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return;
        }
        LocalEvent existing = localEventMap.get(eventId);
        if (existing == null) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("local_event.delete_failed.title"),
                    LocalizationManager.tr("local_event.delete_failed.not_found"));
            return;
        }

        try {
            LocalEventRepository.delete(eventId);
            localEventMap.remove(eventId);
            Popup.INFORMATION.showAndWait(
                    LocalizationManager.tr("local_event.delete_success.title"),
                    LocalizationManager.tr("local_event.delete_success.message"));
        } catch (IOException e) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("local_event.delete_failed.title"),
                    LocalizationManager.tr("local_event.delete_failed.message", e.getMessage()));
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
                // Try to load from appropriate config store based on current mode
                String eventJson = AppModeManager.isLocalMode() 
                    ? LocalConfigurationStore.getEventData()
                    : ILoppisConfigurationStore.getEventData();
                    
                V1Event event = V1Event.fromJson(eventJson);
                if (event != null && event.getId().equals(eventId)) {
                    return event;
                }
            } catch (IOException e) {
                Popup.FATAL.showAndWait(
                        LocalizationManager.tr("error.load_saved_event.title"),
                        LocalizationManager.tr("error.load_saved_event.message",
                                AppPaths.getConfigDir().toString(),
                                e.getMessage()));
            }
            return null;
        }

        return EventUtils.findEventById(eventList, eventId);
    }

    private void configureLocalMode(String eventId, V1RevenueSplit split) {
        AppModeManager.setMode(AppMode.LOCAL);
        AppModeManager.setEventId(eventId);

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

    private void configureOnlineMode(String eventId, V1Event event, V1RevenueSplit split) {
        boolean online = se.goencoder.loppiskassan.rest.ConnectivityChecker.isOnline();

        se.goencoder.loppiskassan.storage.CachedOnlineEvent cached =
                se.goencoder.loppiskassan.storage.OnlineEventCache.loadCachedEvent(eventId);
        boolean cacheFresh = cached != null
                && !cached.isExpired(se.goencoder.loppiskassan.storage.OnlineEventCache.CACHE_TTL_MS);
        boolean hasCachedApiKey = cached != null && cached.hasApiKey();

        if (!online) {
            if (cached == null || !hasCachedApiKey) {
                Popup.ERROR.showAndWait(
                        LocalizationManager.tr("offline.no_cache.title"),
                        LocalizationManager.tr("offline.no_cache.message"));
                return;
            }

            if (!cacheFresh) {
                boolean proceed = Popup.CONFIRM.showConfirmDialog(
                        LocalizationManager.tr("offline.cache_expired.title"),
                        LocalizationManager.tr("offline.cache_expired.message",
                                cached.getCacheAgeDescription()));
                if (!proceed) {
                    return;
                }
            }

            openRegisterWithCachedCredentials(eventId, event, split, cached, true);
            return;
        }

        if (cacheFresh && hasCachedApiKey) {
            openRegisterWithCachedCredentials(eventId, event, split, cached, false);
            return;
        }

        boolean rememberDefault = hasCachedApiKey;
        CashierCodeDialog.Result result = promptForCashierCode(
                event,
                rememberDefault,
                "cashier_code.dialog.message_missing"
        );
        if (result == null || result.getCode().isBlank()) {
            AuthErrorHandler.endPrompt();
            return;
        }

        try {
            ApiKeyServiceApi apiKeyServiceApi = ApiHelper.INSTANCE.getApiKeyServiceApi();
            V1GetApiKeyResponse response = apiKeyServiceApi.apiKeyServiceGetApiKey(eventId, result.getCode(), null);

            if (response == null || response.getApiKey() == null || response.getApiKey().isBlank()) {
                Popup.ERROR.showAndWait(
                        LocalizationManager.tr("error.fetch_token.title"),
                        LocalizationManager.tr("error.fetch_token.message", ""));
                return;
            }

            ApiHelper.INSTANCE.setCurrentApiKey(response.getApiKey());
            AppModeManager.setEventId(eventId);
            if (result.isRemember()) {
                ILoppisConfigurationStore.setApiKey(response.getApiKey());
            } else {
                ILoppisConfigurationStore.setApiKey(null);
            }

            fetchApprovedSellers(eventId);

            String cachedApiKey = result.isRemember() ? response.getApiKey() : "";
            String cachedSellers = result.isRemember() ? ILoppisConfigurationStore.getApprovedSellers() : "";

            se.goencoder.loppiskassan.storage.OnlineEventCache.cacheEvent(
                    event,
                    cachedApiKey,
                    cachedSellers,
                    split
            );

            Popup.INFORMATION.showAndWait(
                    LocalizationManager.tr("info.register_ready.title"),
                    LocalizationManager.tr("info.register_ready.message"));

            view.setCashierButtonEnabled(false);
            view.setRegisterOpened(true);
            view.showActiveEventInfo(event, split);
            view.setChangeEventButtonVisible(true);
            view.setCachedCredentialsStatus(result.isRemember());
            se.goencoder.loppiskassan.service.BackgroundSyncManager.getInstance().ensureRunning(eventId);
        } catch (Exception ex) {
            if (ex instanceof ApiException apiEx) {
                if (AuthErrorHandler.isInvalidCashierCode(apiEx)) {
                    Popup.ERROR.showAndWait(
                            LocalizationManager.tr("cashier_code.exchange_invalid.title"),
                            LocalizationManager.tr("cashier_code.exchange_invalid.message"));
                } else {
                    Popup.ERROR.showAndWait(
                            LocalizationManager.tr("error.fetch_token.title"),
                            apiEx);
                }
            } else {
                LocalConfigurationStore.reset();
                ILoppisConfigurationStore.reset();
                Popup.ERROR.showAndWait(LocalizationManager.tr("error.generic.title"), ex.getMessage());
            }
        } finally {
            AuthErrorHandler.endPrompt();
        }
    }

    private void openRegisterWithCachedCredentials(String eventId,
                                                   V1Event event,
                                                   V1RevenueSplit split,
                                                   se.goencoder.loppiskassan.storage.CachedOnlineEvent cached,
                                                   boolean offline) {
        if (cached == null || !cached.hasApiKey()) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("offline.no_cache.title"),
                    LocalizationManager.tr("offline.no_cache.message"));
            return;
        }

        ApiHelper.INSTANCE.setCurrentApiKey(cached.getApiKey());
        ILoppisConfigurationStore.setApiKey(cached.getApiKey());
        AppModeManager.setEventId(eventId);

        V1RevenueSplit activeSplit = split;
        try {
            if (cached.getRevenueSplitJson() != null && !cached.getRevenueSplitJson().isEmpty()) {
                activeSplit = V1RevenueSplit.fromJson(cached.getRevenueSplitJson());
                ILoppisConfigurationStore.setRevenueSplit(activeSplit.toJson());
            }
        } catch (IOException e) {
            // Ignore - use provided split
        }

        if (cached.hasApprovedSellers()) {
            ILoppisConfigurationStore.setApprovedSellers(cached.getApprovedSellersJson());
        } else if (!offline) {
            try {
                fetchApprovedSellers(eventId);
                se.goencoder.loppiskassan.storage.OnlineEventCache.cacheEvent(
                        event,
                        cached.getApiKey(),
                        ILoppisConfigurationStore.getApprovedSellers(),
                        activeSplit
                );
            } catch (ApiException ex) {
                Popup.ERROR.showAndWait(
                        LocalizationManager.tr("error.fetch_token.title"),
                        ex);
                return;
            }
        }

        if (offline) {
            Popup.WARNING.showAndWait(
                    LocalizationManager.tr("offline.register_opened.title"),
                    LocalizationManager.tr("offline.register_opened.message"));
        } else {
            Popup.INFORMATION.showAndWait(
                    LocalizationManager.tr("info.register_ready.title"),
                    LocalizationManager.tr("info.register_ready.message"));
        }

        view.setCashierButtonEnabled(false);
        view.setRegisterOpened(true);
        view.showActiveEventInfo(event, activeSplit);
        view.setChangeEventButtonVisible(true);
        view.setCachedCredentialsStatus(true);

        se.goencoder.loppiskassan.service.BackgroundSyncManager.getInstance().ensureRunning(eventId);
    }

    private CashierCodeDialog.Result promptForCashierCode(V1Event event,
                                                          boolean rememberDefault,
                                                          String messageKey) {
        if (!AuthErrorHandler.beginPrompt()) {
            return null;
        }

        String eventName = event != null && event.getName() != null ? event.getName() : "";
        java.awt.Component parent = view instanceof java.awt.Component ? (java.awt.Component) view : null;

        CashierCodeDialog.Result result = CashierCodeDialog.showDialog(
                parent,
                LocalizationManager.tr("cashier_code.dialog.title"),
                LocalizationManager.tr(messageKey, eventName),
                rememberDefault
        );

        if (result == null) {
            AuthErrorHandler.endPrompt();
        }
        return result;
    }

    private void fetchApprovedSellers(String eventId) throws ApiException {
        log.info("=== DiscoveryTabController fetchApprovedSellers START === event: " + eventId);
        
        VendorServiceApi api = ApiHelper.INSTANCE.getVendorServiceApi();
        Set<Integer> approvedSellers = new HashSet<>();
        String nextPageToken = "";
        int pageCount = 0;
        
        // Use vendors:filter with status="approved" (same endpoint as Android/Frontend)
        do {
            pageCount++;
            
            V1VendorFilter filter = new V1VendorFilter();
            filter.setStatus("approved");
            
            V1Pagination pagination = new V1Pagination();
            pagination.setPageSize(APPROVED_SELLERS_PAGE_SIZE);
            if (!nextPageToken.isEmpty()) {
                pagination.setPageToken(nextPageToken);
            }
            
            VendorServiceFilterVendorsBody body = new VendorServiceFilterVendorsBody();
            body.setFilter(filter);
            body.setPagination(pagination);
            
            V1FilterVendorsResponse res = api.vendorServiceFilterVendors(eventId, body);
            
            if (res.getVendors() != null) {
                for (V1Vendor vendor : res.getVendors()) {
                    approvedSellers.add(vendor.getSellerNumber());
                }
            }
            
            nextPageToken = res.getNextPageToken();
            
        } while (nextPageToken != null && !nextPageToken.isEmpty());
        
        log.info(String.format("=== DiscoveryTabController fetchApprovedSellers END === %d pages, %d APPROVED", 
            pageCount, approvedSellers.size()));
        
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("approvedSellers", new JSONArray(approvedSellers));
        ILoppisConfigurationStore.setApprovedSellers(jsonObject.toString());
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

        LocalConfigurationStore.setRevenueSplit(split.toJson());

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

        V1RevenueSplit revenueSplit = null;
        String warningMessage = null;

        if (!state.isOfflineMode()) {
            try {
                V1GetMarketResponse response = ApiHelper.INSTANCE.getApprovedMarketServiceApi()
                        .approvedMarketServiceGetMarket(selectedEvent.getMarketId());
                V1Market market = response.getMarket();
                if (market != null) {
                    revenueSplit = market.getRevenueSplit();
                }
            } catch (ApiException e) {
                warningMessage = e.getMessage();
            }
        }

        if (revenueSplit == null) {
            revenueSplit = loadCachedRevenueSplit(selectedEvent.getId());
        }

        if (revenueSplit == null) {
            if (state.isOfflineMode()) {
                Popup.WARNING.showAndWait(
                        LocalizationManager.tr("offline.no_cache.title"),
                        LocalizationManager.tr("offline.no_cache.message"));
            } else if (warningMessage != null) {
                Popup.WARNING.showAndWait(
                        LocalizationManager.tr("warning.fetch_market_info.title"),
                        LocalizationManager.tr("warning.fetch_market_info.message", warningMessage));
            }
            revenueSplit = new V1RevenueSplit();
        } else {
            ILoppisConfigurationStore.setRevenueSplit(revenueSplit.toJson());
        }

        float marketOwner = revenueSplit.getMarketOwnerPercentage() == null ? 0f : revenueSplit.getMarketOwnerPercentage();
        float vendor = revenueSplit.getVendorPercentage() == null ? 0f : revenueSplit.getVendorPercentage();
        float platform = revenueSplit.getPlatformProviderPercentage() == null ? 0f : revenueSplit.getPlatformProviderPercentage();

        view.setRevenueSplit(marketOwner, vendor, platform);
    }

    private V1RevenueSplit loadCachedRevenueSplit(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return null;
        }

        se.goencoder.loppiskassan.storage.CachedOnlineEvent cached =
                se.goencoder.loppiskassan.storage.OnlineEventCache.loadCachedEvent(eventId);
        if (cached == null || cached.isExpired(se.goencoder.loppiskassan.storage.OnlineEventCache.CACHE_TTL_MS)) {
            return null;
        }

        String splitJson = cached.getRevenueSplitJson();
        if (splitJson == null || splitJson.isBlank()) {
            return null;
        }

        try {
            return V1RevenueSplit.fromJson(splitJson);
        } catch (IOException e) {
            return null;
        }
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

            // Show upload dialog via view
            BulkUploadResult result = view.showBulkUploadDialog(localEvent);

            if (result != null && result.hasResults()) {
                if (result.isFullSuccess()) {
                    Popup.INFORMATION.showAndWait(
                        LocalizationManager.tr("bulk_upload.success"),
                        String.format("✅ %d items uploaded successfully", 
                            result.acceptedItems.size()));
                } else if (result.isPartialSuccess()) {
                    Popup.WARNING.showAndWait(
                        LocalizationManager.tr("bulk_upload.summary"),
                        result.getSummaryText());
                } else if (!result.errorMessages.isEmpty()) {
                    Popup.ERROR.showAndWait(
                        LocalizationManager.tr("error"),
                        String.join("\n", result.errorMessages));
                }
            }
        } catch (IOException e) {
            Popup.ERROR.showAndWait(
                LocalizationManager.tr("error"),
                "Failed to load local event: " + e.getMessage());
        }
    }
}
