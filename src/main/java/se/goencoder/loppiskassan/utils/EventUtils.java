package se.goencoder.loppiskassan.utils;

import se.goencoder.iloppis.model.Event;
import se.goencoder.loppiskassan.localization.LocalizationManager;

import java.time.OffsetDateTime;

import java.util.List;

public class EventUtils {
    public static Event findEventById(List<Event> events, String eventId) {
        return events.stream()
                .filter(event -> eventId.equals(event.getId()))
                .findFirst()
                .orElse(null);
    }

    public static void populateOfflineEvent(Event event) {
        event.setId("offline");
        event.setName(LocalizationManager.tr("event.offline.name"));
        event.setDescription(LocalizationManager.tr("event.offline.description"));
        event.setAddressCity(LocalizationManager.tr("event.no_city"));
        event.setAddressStreet(LocalizationManager.tr("event.no_street"));
        event.setStartTime(OffsetDateTime.now());
        event.setEndTime(null);
    }
}

