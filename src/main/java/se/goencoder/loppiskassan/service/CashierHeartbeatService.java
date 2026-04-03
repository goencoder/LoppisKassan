package se.goencoder.loppiskassan.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import se.goencoder.loppiskassan.rest.ApiHelper;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Sends cashier presence heartbeats to backend live-ops endpoint.
 * Uses the shared {@link ApiHelper} HTTP client — authentication is injected
 * automatically by the interceptor.
 */
public class CashierHeartbeatService {

    private static final Logger log = Logger.getLogger(CashierHeartbeatService.class.getName());
    private static final MediaType JSON_MEDIA_TYPE = MediaType.get("application/json");
    private static final Gson GSON = new Gson();

    private final okhttp3.OkHttpClient httpClient;
    private final String baseUrl;

    public record HeartbeatResult(String displayName) {}

    /** Production constructor — uses the shared ApiHelper client and base URL. */
    public CashierHeartbeatService() {
        this.httpClient = ApiHelper.INSTANCE.getHttpClient();
        this.baseUrl = ApiHelper.INSTANCE.getBasePath();
    }

    /** Test constructor — allows overriding the HTTP client and base URL. */
    CashierHeartbeatService(okhttp3.OkHttpClient httpClient, String baseUrl) {
        this.httpClient = httpClient;
        this.baseUrl = baseUrl;
    }

    public HeartbeatResult sendHeartbeat(
            String eventId,
            String clientState,
            int pendingPurchasesCount,
            String clientType,
            String displayName
    ) throws IOException {
        return sendHeartbeat(eventId, clientState, pendingPurchasesCount, clientType, displayName, null, null, null);
    }

    /**
     * Extended heartbeat that also carries register session lifecycle fields.
     *
     * @param lifecycleEventType one of OPEN, SYNC, CLOSE_REQUESTED, CLOSE_CONFIRMED — or null to omit
     * @param registerId         stable register name/id — or null to omit
     * @param sessionId          active session id — or null to omit
     */
    public HeartbeatResult sendHeartbeat(
            String eventId,
            String clientState,
            int pendingPurchasesCount,
            String clientType,
            String displayName,
            String lifecycleEventType,
            String registerId,
            String sessionId
    ) throws IOException {
        String apiKey = ApiHelper.INSTANCE.getCurrentApiKey();
        if (eventId == null || eventId.isBlank() || apiKey == null || apiKey.isBlank()) {
            return new HeartbeatResult(displayName);
        }

        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String encodedEventId = URLEncoder.encode(eventId, StandardCharsets.UTF_8).replace("+", "%20");
        String url = normalizedBase + "/v1/events/" + encodedEventId + "/cashier-presence:heartbeat";

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event_id", eventId);
        payload.put("client_state", clientState);
        payload.put("pending_purchases_count", Math.max(0, pendingPurchasesCount));
        payload.put("client_type", clientType);
        payload.put("display_name", displayName == null ? "" : displayName);
        if (lifecycleEventType != null && !lifecycleEventType.isBlank()) {
            payload.put("lifecycle_event_type", lifecycleEventType);
        }
        if (registerId != null && !registerId.isBlank()) {
            payload.put("register_id", registerId);
        }
        if (sessionId != null && !sessionId.isBlank()) {
            payload.put("session_id", sessionId);
        }

        String json = GSON.toJson(payload);
        RequestBody body = RequestBody.create(JSON_MEDIA_TYPE, json.getBytes(StandardCharsets.UTF_8));

        // Auth header injected automatically by AuthInterceptor on the shared client
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", JSON_MEDIA_TYPE.toString())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                log.warning("Heartbeat failed with status " + response.code() + ": " + responseBody);
                return new HeartbeatResult(displayName);
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            if (responseBody.isBlank()) {
                return new HeartbeatResult(displayName);
            }

            String nextDisplayName = extractDisplayName(responseBody, displayName);
            return new HeartbeatResult(nextDisplayName);
        }
    }

    private String extractDisplayName(String json, String fallback) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("display_name") && !root.get("display_name").isJsonNull()) {
                String value = root.get("display_name").getAsString().trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
            if (root.has("displayName") && !root.get("displayName").isJsonNull()) {
                String value = root.get("displayName").getAsString().trim();
                if (!value.isEmpty()) {
                    return value;
                }
            }
        } catch (Exception ignored) {
            // Ignore decode issues and keep current name.
        }
        return fallback;
    }
}
