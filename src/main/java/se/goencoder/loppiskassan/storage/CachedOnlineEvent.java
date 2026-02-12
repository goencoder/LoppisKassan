package se.goencoder.loppiskassan.storage;

import org.json.JSONObject;
import se.goencoder.iloppis.model.V1Event;
import se.goencoder.iloppis.model.V1RevenueSplit;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a cached iLoppis online event with associated credentials.
 * Stored as metadata.json under ~/.loppiskassan/events/{eventId}/.
 */
public class CachedOnlineEvent {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final String eventId;
    private final String eventName;
    private final String description;
    private final String addressStreet;
    private final String addressCity;
    private final String marketId;
    private final String apiKey;
    private final String approvedSellersJson;
    private final String revenueSplitJson;
    private final OffsetDateTime startTime;
    private final OffsetDateTime endTime;
    private final OffsetDateTime cachedAt;

    public CachedOnlineEvent(
            String eventId,
            String eventName,
            String description,
            String addressStreet,
            String addressCity,
            String marketId,
            String apiKey,
            String approvedSellersJson,
            String revenueSplitJson,
            OffsetDateTime startTime,
            OffsetDateTime endTime,
            OffsetDateTime cachedAt
    ) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.description = description;
        this.addressStreet = addressStreet;
        this.addressCity = addressCity;
        this.marketId = marketId;
        this.apiKey = apiKey;
        this.approvedSellersJson = approvedSellersJson;
        this.revenueSplitJson = revenueSplitJson;
        this.startTime = startTime;
        this.endTime = endTime;
        this.cachedAt = cachedAt;
    }

    public String getEventId() {
        return eventId;
    }

    public String getEventName() {
        return eventName;
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

    public String getMarketId() {
        return marketId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getApprovedSellersJson() {
        return approvedSellersJson;
    }

    public String getRevenueSplitJson() {
        return revenueSplitJson;
    }

    public OffsetDateTime getStartTime() {
        return startTime;
    }

    public OffsetDateTime getEndTime() {
        return endTime;
    }

    public OffsetDateTime getCachedAt() {
        return cachedAt;
    }

    /**
     * Check if this cache entry is older than maxAge milliseconds.
     */
    public boolean isExpired(long maxAgeMs) {
        if (cachedAt == null) return true;
        long ageMs = System.currentTimeMillis() - cachedAt.toInstant().toEpochMilli();
        return ageMs > maxAgeMs;
    }

    /**
     * Get a human-readable description of cache age.
     */
    public String getCacheAgeDescription() {
        if (cachedAt == null) return "unknown";
        long ageMs = System.currentTimeMillis() - cachedAt.toInstant().toEpochMilli();
        long hours = ageMs / (60 * 60 * 1000);
        long days = hours / 24;
        
        if (days > 0) {
            return days + " " + (days == 1 ? "day" : "days");
        } else if (hours > 0) {
            return hours + " " + (hours == 1 ? "hour" : "hours");
        } else {
            return "less than 1 hour";
        }
    }

    /**
     * Convert to V1Event for display in the UI.
     */
    public V1Event toV1Event() {
        V1Event event = new V1Event();
        event.setId(eventId);
        event.setName(eventName);
        event.setDescription(description);
        event.setAddressStreet(addressStreet);
        event.setAddressCity(addressCity);
        event.setMarketId(marketId);
        event.setStartTime(startTime);
        event.setEndTime(endTime);
        return event;
    }

    /**
     * Serialize to JSON string for storage.
     */
    public String toJsonString() {
        JSONObject json = new JSONObject();
        json.put("eventId", eventId);
        json.put("eventName", eventName != null ? eventName : "");
        json.put("description", description != null ? description : "");
        json.put("addressStreet", addressStreet != null ? addressStreet : "");
        json.put("addressCity", addressCity != null ? addressCity : "");
        json.put("marketId", marketId != null ? marketId : "");
        json.put("apiKey", apiKey != null ? apiKey : "");
        json.put("approvedSellersJson", approvedSellersJson != null ? approvedSellersJson : "");
        json.put("revenueSplitJson", revenueSplitJson != null ? revenueSplitJson : "");
        if (startTime != null) {
            json.put("startTime", startTime.format(DATE_FORMATTER));
        }
        if (endTime != null) {
            json.put("endTime", endTime.format(DATE_FORMATTER));
        }
        if (cachedAt != null) {
            json.put("cachedAt", cachedAt.format(DATE_FORMATTER));
        }
        return json.toString();
    }

    /**
     * Deserialize from JSON string.
     */
    public static CachedOnlineEvent fromJsonString(String json) throws IOException {
        JSONObject obj = new JSONObject(json);
        String eventId = obj.optString("eventId", "");
        String eventName = obj.optString("eventName", "");
        String description = obj.optString("description", "");
        String addressStreet = obj.optString("addressStreet", "");
        String addressCity = obj.optString("addressCity", "");
        String marketId = obj.optString("marketId", "");
        String apiKey = obj.optString("apiKey", "");
        String approvedSellersJson = obj.optString("approvedSellersJson", "");
        String revenueSplitJson = obj.optString("revenueSplitJson", "");

        OffsetDateTime startTime = null;
        String startTimeRaw = obj.optString("startTime", "");
        if (!startTimeRaw.isBlank()) {
            startTime = OffsetDateTime.parse(startTimeRaw, DATE_FORMATTER);
        }

        OffsetDateTime endTime = null;
        String endTimeRaw = obj.optString("endTime", "");
        if (!endTimeRaw.isBlank()) {
            endTime = OffsetDateTime.parse(endTimeRaw, DATE_FORMATTER);
        }

        OffsetDateTime cachedAt = null;
        String cachedAtRaw = obj.optString("cachedAt", "");
        if (!cachedAtRaw.isBlank()) {
            cachedAt = OffsetDateTime.parse(cachedAtRaw, DATE_FORMATTER);
        }

        return new CachedOnlineEvent(
                eventId,
                eventName,
                description,
                addressStreet,
                addressCity,
                marketId,
                apiKey,
                approvedSellersJson,
                revenueSplitJson,
                startTime,
                endTime,
                cachedAt
        );
    }
}
