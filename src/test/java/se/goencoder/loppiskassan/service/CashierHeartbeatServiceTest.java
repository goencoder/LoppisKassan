package se.goencoder.loppiskassan.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import se.goencoder.iloppis.api.StatsServiceApi;
import se.goencoder.iloppis.model.V1RegisterLifecycleEventType;
import se.goencoder.iloppis.model.V1UpdateCashierPresenceResponse;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.rest.FixedApiClient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CashierHeartbeatServiceTest {

    @Test
    void sendHeartbeat_postsExpectedPayload_andParsesDisplayName() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> methodRef = new AtomicReference<>();
        AtomicReference<String> authRef = new AtomicReference<>();
        AtomicReference<String> bodyRef = new AtomicReference<>();
        String responseJson = new V1UpdateCashierPresenceResponse()
                .displayName("minty raven")
                .registerId("register-a")
                .sessionId("session-a")
                .lifecycleEventType(V1RegisterLifecycleEventType.REGISTER_LIFECYCLE_EVENT_TYPE_SYNC)
                .toJson();

        server.createContext("/v1/events/evt-123/cashier-presence:heartbeat", exchange -> {
            methodRef.set(exchange.getRequestMethod());
            authRef.set(exchange.getRequestHeaders().getFirst("Authorization"));
            bodyRef.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, 200, responseJson);
        });
        server.start();

        String testBaseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        String previousApiKey = ApiHelper.INSTANCE.getCurrentApiKey();
        ApiHelper.INSTANCE.setCurrentApiKey("test-api-key");

        try {
            FixedApiClient client = new FixedApiClient();
            client.setBasePath(testBaseUrl);
            CashierHeartbeatService service = new CashierHeartbeatService(new StatsServiceApi(client));
            CashierHeartbeatService.HeartbeatResult result = service.sendHeartbeat(
                    "evt-123",
                    "CASHIER_CLIENT_STATE_ACTIVE_TRANSACTION",
                    -3,
                    "CASHIER_CLIENT_TYPE_JAVA",
                    "old-name",
                    "REGISTER_LIFECYCLE_EVENT_TYPE_SYNC",
                    "register-a",
                    "session-a"
            );

            assertNotNull(result);
            assertEquals("minty raven", result.displayName());
            assertEquals("POST", methodRef.get());
            assertEquals("Bearer test-api-key", authRef.get());

            JsonObject payload = JsonParser.parseString(bodyRef.get()).getAsJsonObject();
            assertEquals("CASHIER_CLIENT_STATE_ACTIVE_TRANSACTION", payload.get("clientState").getAsString());
            assertEquals(0, payload.get("pendingPurchasesCount").getAsInt());
            assertEquals("CASHIER_CLIENT_TYPE_JAVA", payload.get("clientType").getAsString());
            assertEquals("old-name", payload.get("displayName").getAsString());
            assertEquals("REGISTER_LIFECYCLE_EVENT_TYPE_SYNC", payload.get("lifecycleEventType").getAsString());
            assertEquals("register-a", payload.get("registerId").getAsString());
            assertEquals("session-a", payload.get("sessionId").getAsString());
        } finally {
            if (previousApiKey != null) {
                ApiHelper.INSTANCE.setCurrentApiKey(previousApiKey);
            } else {
                ApiHelper.INSTANCE.clearCurrentApiKey();
            }
            server.stop(0);
        }
    }

    @Test
    void sendHeartbeat_returnsFallbackDisplayName_onErrorResponse() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/events/evt-123/cashier-presence:heartbeat", exchange ->
                writeJson(exchange, 500, "{\"message\":\"fail\"}")
        );
        server.start();

        String testBaseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        String previousApiKey = ApiHelper.INSTANCE.getCurrentApiKey();
        ApiHelper.INSTANCE.setCurrentApiKey("test-api-key");

        try {
            FixedApiClient client = new FixedApiClient();
            client.setBasePath(testBaseUrl);
            CashierHeartbeatService service = new CashierHeartbeatService(new StatsServiceApi(client));
            CashierHeartbeatService.HeartbeatResult result = service.sendHeartbeat(
                    "evt-123",
                    "CASHIER_CLIENT_STATE_IDLE",
                    0,
                    "CASHIER_CLIENT_TYPE_JAVA",
                    "keep-name"
            );

            assertNotNull(result);
            assertEquals("keep-name", result.displayName());
        } finally {
            if (previousApiKey != null) {
                ApiHelper.INSTANCE.setCurrentApiKey(previousApiKey);
            } else {
                ApiHelper.INSTANCE.clearCurrentApiKey();
            }
            server.stop(0);
        }
    }

    private static void writeJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
