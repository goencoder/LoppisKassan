package se.goencoder.loppiskassan.utils;

import se.goencoder.iloppis.model.Event;

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
        event.setName("Offline-loppis");
        event.setDescription("Detta är en offline-loppis. Ingen internetanslutning krävs.");
        event.setAddressCity("okänd stad");
        event.setAddressStreet("okänd gata");
        event.setStartTime(OffsetDateTime.now());
        event.setEndTime(null);
    }
}

