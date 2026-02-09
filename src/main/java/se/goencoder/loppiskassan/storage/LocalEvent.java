package se.goencoder.loppiskassan.storage;

import org.json.JSONObject;
import se.goencoder.iloppis.model.V1RevenueSplit;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class LocalEvent {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final String eventId;
    private final LocalEventType eventType;
    private final String name;
    private final String description;
    private final String addressStreet;
    private final String addressCity;
    private final OffsetDateTime createdAt;
    private final V1RevenueSplit revenueSplit;

    public LocalEvent(
            String eventId,
            LocalEventType eventType,
            String name,
            String description,
            String addressStreet,
            String addressCity,
            OffsetDateTime createdAt,
            V1RevenueSplit revenueSplit
    ) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.name = name;
        this.description = description;
        this.addressStreet = addressStreet;
        this.addressCity = addressCity;
        this.createdAt = createdAt;
        this.revenueSplit = revenueSplit;
    }

    public String getEventId() {
        return eventId;
    }

    public LocalEventType getEventType() {
        return eventType;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getAddressStreet() {
        return addressStreet;
    }

    public String getAddressCity() {
        return addressCity;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public V1RevenueSplit getRevenueSplit() {
        return revenueSplit;
    }

    public String toJsonString() {
        JSONObject json = new JSONObject();
        json.put("eventId", eventId);
        json.put("eventType", eventType.name());
        json.put("name", name);
        json.put("description", description != null ? description : "");
        json.put("addressStreet", addressStreet != null ? addressStreet : "");
        json.put("addressCity", addressCity != null ? addressCity : "");
        if (createdAt != null) {
            json.put("createdAt", createdAt.format(DATE_FORMATTER));
        }
        if (revenueSplit != null) {
            json.put("revenueSplit", new JSONObject(revenueSplit.toJson()));
        }
        return json.toString();
    }

    public static LocalEvent fromJsonString(String json) throws IOException {
        JSONObject obj = new JSONObject(json);
        String eventId = obj.optString("eventId", "");
        String eventTypeRaw = obj.optString("eventType", LocalEventType.LOCAL.name());
        LocalEventType eventType = LocalEventType.valueOf(eventTypeRaw.toUpperCase(Locale.ROOT));
        String name = obj.optString("name", "");
        String description = obj.optString("description", "");
        String addressStreet = obj.optString("addressStreet", "");
        String addressCity = obj.optString("addressCity", "");
        OffsetDateTime createdAt = null;
        String createdAtRaw = obj.optString("createdAt", "");
        if (!createdAtRaw.isBlank()) {
            createdAt = OffsetDateTime.parse(createdAtRaw, DATE_FORMATTER);
        }
        V1RevenueSplit revenueSplit = null;
        if (obj.has("revenueSplit")) {
            revenueSplit = V1RevenueSplit.fromJson(obj.getJSONObject("revenueSplit").toString());
        }
        return new LocalEvent(eventId, eventType, name, description, addressStreet, addressCity, createdAt, revenueSplit);
    }
}
