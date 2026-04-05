package se.goencoder.loppiskassan.service;

import se.goencoder.iloppis.api.StatsServiceApi;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.StatsServiceUpdateCashierPresenceBody;
import se.goencoder.iloppis.model.V1CashierClientState;
import se.goencoder.iloppis.model.V1CashierClientType;
import se.goencoder.iloppis.model.V1RegisterLifecycleEventType;
import se.goencoder.iloppis.model.V1UpdateCashierPresenceResponse;
import se.goencoder.loppiskassan.rest.ApiHelper;

import java.util.logging.Logger;

/**
 * Sends cashier presence heartbeats to backend live-ops endpoint.
 * Uses the shared {@link ApiHelper} HTTP client — authentication is injected
 * automatically by the interceptor.
 */
public class CashierHeartbeatService {

    private static final Logger log = Logger.getLogger(CashierHeartbeatService.class.getName());

    private final StatsServiceApi statsServiceApi;

    public record HeartbeatResult(String displayName, boolean success) {}

    /** Production constructor — uses the shared generated StatsServiceApi. */
    public CashierHeartbeatService() {
        this(ApiHelper.INSTANCE.getStatsServiceApi());
    }

    /** Test constructor — allows overriding the generated API instance. */
    CashierHeartbeatService(StatsServiceApi statsServiceApi) {
        this.statsServiceApi = statsServiceApi;
    }

    public HeartbeatResult sendHeartbeat(
            String eventId,
            String clientState,
            int pendingPurchasesCount,
            String clientType,
            String displayName
    ) {
        return sendHeartbeat(eventId, clientState, pendingPurchasesCount, clientType, displayName, null, null, null);
    }

    /**
     * Extended heartbeat that also carries register session lifecycle fields.
     *
    * @param lifecycleEventType enum wire value string (for example
    *                           REGISTER_LIFECYCLE_EVENT_TYPE_SYNC,
    *                           REGISTER_LIFECYCLE_EVENT_TYPE_CLOSE_REQUESTED,
    *                           REGISTER_LIFECYCLE_EVENT_TYPE_CLOSE_CONFIRMED) —
    *                           or null to omit
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
    ) {
        String apiKey = ApiHelper.INSTANCE.getCurrentApiKey();
        if (eventId == null || eventId.isBlank() || apiKey == null || apiKey.isBlank()) {
            return new HeartbeatResult(displayName, false);
        }

        V1CashierClientState mappedClientState = mapClientState(clientState);
        V1CashierClientType mappedClientType = mapClientType(clientType);

        if (mappedClientState == null || mappedClientType == null) {
            log.warning("Heartbeat skipped due to unsupported enum mapping");
            return new HeartbeatResult(displayName, false);
        }

        StatsServiceUpdateCashierPresenceBody request = new StatsServiceUpdateCashierPresenceBody()
                .clientState(mappedClientState)
                .pendingPurchasesCount(Math.max(0, pendingPurchasesCount))
                .clientType(mappedClientType)
                .displayName(displayName == null ? "" : displayName);

        if (lifecycleEventType != null && !lifecycleEventType.isBlank()) {
            V1RegisterLifecycleEventType mappedLifecycleEventType = mapLifecycleEventType(lifecycleEventType);
            if (mappedLifecycleEventType != null) {
                request.lifecycleEventType(mappedLifecycleEventType);
            }
        }
        if (registerId != null && !registerId.isBlank()) {
            request.registerId(registerId);
        }
        if (sessionId != null && !sessionId.isBlank()) {
            request.sessionId(sessionId);
        }

        try {
            V1UpdateCashierPresenceResponse response = statsServiceApi.statsServiceUpdateCashierPresence(eventId, request);
            return new HeartbeatResult(extractDisplayName(response, displayName), true);
        } catch (ApiException e) {
            log.warning("Heartbeat failed with status " + e.getCode() + ": " + e.getResponseBody());
            return new HeartbeatResult(displayName, false);
        }
    }

    private static V1CashierClientState mapClientState(String value) {
        try {
            return V1CashierClientState.fromValue(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static V1CashierClientType mapClientType(String value) {
        try {
            return V1CashierClientType.fromValue(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static V1RegisterLifecycleEventType mapLifecycleEventType(String value) {
        try {
            return V1RegisterLifecycleEventType.fromValue(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String extractDisplayName(V1UpdateCashierPresenceResponse response, String fallback) {
        if (response == null || response.getDisplayName() == null) {
            return fallback;
        }
        String value = response.getDisplayName().trim();
        return value.isEmpty() ? fallback : value;
    }
}
