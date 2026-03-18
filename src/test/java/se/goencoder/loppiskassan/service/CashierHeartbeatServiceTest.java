package se.goencoder.loppiskassan.service;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import se.goencoder.loppiskassan.rest.ApiHelper;

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

        server.createContext("/v1/events/evt-123/cashier-presence:heartbeat", exchange -> {
            methodRef.set(exchange.getRequestMethod());
            authRef.set(exchange.getRequestHeaders().getFirst("Authorization"));
            bodyRef.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            writeJson(exchange, 200, "{\"display_name\":\"minty raven\"}");
        });
        server.start();

        String testBaseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        String previousApiKey = ApiHelper.INSTANCE.getCurrentApiKey();
        ApiHelper.INSTANCE.setCurrentApiKey("test-api-key");

        try {
            // Use test constructor with shared OkHttp client (has AuthInterceptor) but local base URL
            CashierHeartbeatService service = new CashierHeartbeatService(
                    ApiHelper.INSTANCE.getHttpClient(), testBaseUrl);
            CashierHeartbeatService.HeartbeatResult result = service.sendHeartbeat(
                    "evt-123",
                    "CASHIER_CLIENT_STATE_ACTIVE_TRANSACTION",
                    -3,
                    "CASHIER_CLIENT_TYPE_JAVA",
                    "old-name"
            );

            assertNotNull(result);
            assertEquals("minty raven", result.displayName());
            assertEquals("POST", methodRef.get());
            assertEquals("Bearer test-api-key", authRef.get());

            JsonObject payload = JsonParser.parseString(bodyRef.get()).getAsJsonObject();
            assertEquals("evt-123", payload.get("event_id").getAsString());
            assertEquals("CASHIER_CLIENT_STATE_ACTIVE_TRANSACTION", payload.get("client_state").getAsString());
            assertEquals(0, payload.get("pending_purchases_count").getAsInt());
            assertEquals("CASHIER_CLIENT_TYPE_JAVA", payload.get("client_type").getAsString());
            assertEquals("old-name", payload.get("display_name").getAsString());
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
            CashierHeartbeatService service = new CashierHeartbeatService(
                    ApiHelper.INSTANCE.getHttpClient(), testBaseUrl);
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
