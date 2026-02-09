package se.goencoder.loppiskassan.interactor;

import se.goencoder.iloppis.api.EventServiceApi;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.*;
import se.goencoder.loppiskassan.model.discovery.DiscoveryState;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.storage.LocalEvent;
import se.goencoder.loppiskassan.storage.LocalEventRepository;
import se.goencoder.loppiskassan.localization.LocalizationManager;

import java.io.IOException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Business logic for the discovery tab: loading events, filtering, and searching.
 * <p>
 * This class has no dependencies on Swing components and is fully testable.
 * Configuration management remains in the controller for now.
 */
public class DiscoveryInteractor {

    private final DiscoveryState state;
    private List<V1Event> eventList;
    private Map<String, LocalEvent> localEventMap = new HashMap<>();

    public DiscoveryInteractor(DiscoveryState state) {
        this.state = state;
        this.eventList = new ArrayList<>();
    }

    /**
     * Loads all events (both local and from API) starting from the given date.
     *
     * @param dateFrom the start date for filtering API events (ISO format)
     * @return list of all events
     * @throws IOException if local event loading fails
     * @throws ApiException if API call fails
     */
    public List<V1Event> loadAllEvents(String dateFrom) throws IOException, ApiException {
        if (dateFrom == null || dateFrom.isBlank()) {
            dateFrom = LocalDate.now().toString();
        }

        List<V1Event> newEventList = new ArrayList<>();
        Map<String, LocalEvent> newLocalEventMap = new HashMap<>();

        // Load local events from disk
        List<LocalEvent> localEvents = LocalEventRepository.loadAll();
        for (LocalEvent localEvent : localEvents) {
            V1Event event = toLocalV1Event(localEvent);
            newEventList.add(event);
            newLocalEventMap.put(localEvent.getEventId(), localEvent);
        }

        // Fetch events from API
        EventServiceApi eventApi = ApiHelper.INSTANCE.getEventServiceApi();

        V1EventFilter eventFilter = new V1EventFilter();
        eventFilter.setDateFrom(OffsetDateTime.parse(
                dateFrom.contains("T") ? dateFrom : dateFrom + "T00:00:00+00:00"));

        V1Pagination pagination = new V1Pagination();
        pagination.setPageSize(100);

        V1FilterEventsRequest request = new V1FilterEventsRequest();
        request.setFilter(eventFilter);
        request.setPagination(pagination);

        V1FilterEventsResponse response = eventApi.eventServiceFilterEvents(request);
        List<V1Event> discovered = response.getEvents();
        newEventList.addAll(Objects.requireNonNull(discovered));

        // Store for future reference
        this.eventList = newEventList;
        this.localEventMap = newLocalEventMap;

        // Update state
        state.setEvents(newEventList);
        state.setDateFrom(dateFrom);

        return newEventList;
    }

    /**
     * Finds an event by ID from the loaded event list.
     *
     * @param eventId the event ID
     * @return the event, or null if not found
     */
    public V1Event findEventById(String eventId) {
        if (eventList == null || eventId == null) {
            return null;
        }
        return eventList.stream()
                .filter(e -> eventId.equals(e.getId()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Checks if an event is a local event (not from API).
     *
     * @param eventId the event ID
     * @return true if local event
     */
    public boolean isLocalEvent(String eventId) {
        return localEventMap.containsKey(eventId);
    }

    /**
     * Gets the local event object if it exists.
     *
     * @param eventId the event ID
     * @return the local event, or null if not found
     */
    public LocalEvent getLocalEvent(String eventId) {
        return localEventMap.get(eventId);
    }

    /**
     * Gets the revenue split for a local event, or returns a default.
     *
     * @param eventId the event ID
     * @return the revenue split, or a default split if not found
     */
    public V1RevenueSplit getRevenueSplitForLocalEvent(String eventId) {
        LocalEvent localEvent = localEventMap.get(eventId);
        V1RevenueSplit split = localEvent == null ? null : localEvent.getRevenueSplit();
        
        if (split == null) {
            split = new V1RevenueSplit();
            split.setCharityPercentage(0f);
            split.setMarketOwnerPercentage(10f);
            split.setVendorPercentage(85f);
            split.setPlatformProviderPercentage(5f);
        }
        
        return split;
    }

    /**
     * Creates a default revenue split.
     *
     * @return default revenue split with standard percentages
     */
    public V1RevenueSplit createDefaultRevenueSplit() {
        V1RevenueSplit split = new V1RevenueSplit();
        split.setCharityPercentage(0f);
        split.setMarketOwnerPercentage(10f);
        split.setVendorPercentage(85f);
        split.setPlatformProviderPercentage(5f);
        return split;
    }

    /**
     * Converts a local event to a V1Event for display.
     */
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
}
