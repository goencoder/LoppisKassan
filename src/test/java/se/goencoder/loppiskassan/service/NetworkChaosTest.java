package se.goencoder.loppiskassan.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import se.goencoder.iloppis.invoker.ApiClient;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.storage.PendingItemsStore;
import se.goencoder.loppiskassan.storage.RejectedItemsStore;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real loopback HTTP faults through the production client; no external endpoints. */
@EnabledIfSystemProperty(named = "loppiskassan.network.chaos", matches = "true")
class NetworkChaosTest {
    @TempDir Path directory;
    private static final String EVENT = "synthetic-network-chaos";
    private HttpServer server;
    private String proxyName;
    private final String proxyAdmin = System.getProperty("loppiskassan.chaos.proxy.admin");

    private ExecutorService serverThreads;
    private String previousStorage;
    private String previousBase;
    private String previousKey;
    private ApiClient client;
    private PendingItemsStore pending;
    private BackgroundSyncManager sync;
    private final Map<String, JSONObject> persisted = new ConcurrentHashMap<>();
    private volatile String fault = "none";

    @BeforeEach void setUp() throws Exception {
        previousStorage = System.getProperty("loppiskassan.base.dir");
        System.setProperty("loppiskassan.base.dir", directory.toString());
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverThreads = Executors.newCachedThreadPool();
        server.setExecutor(serverThreads);
        server.createContext("/", this::handle);
        server.start();
        client = ApiHelper.INSTANCE.getSoldItemsServiceApi().getApiClient();
        previousBase = client.getBasePath();
        previousKey = ApiHelper.INSTANCE.getCurrentApiKey();
        if (proxyAdmin != null) {
            assertTrue(proxyAdmin.startsWith("http://127.0.0.1:"));
            proxyName = "sales-chaos-" + java.util.UUID.randomUUID();
            proxyRequest("POST", "/proxies", new JSONObject()
                    .put("name", proxyName).put("listen", "0.0.0.0:8666")
                    .put("upstream", "host.docker.internal:" + server.getAddress().getPort())
                    .put("enabled", true));
            client.setBasePath("http://127.0.0.1:" + Integer.parseInt(
                    System.getProperty("loppiskassan.chaos.proxy.port")));
        } else {
            client.setBasePath("http://127.0.0.1:" + server.getAddress().getPort());
        }
        ApiHelper.INSTANCE.setCurrentApiKey("synthetic-chaos-test-key");
        assertEquals(5000, client.getReadTimeout(), "exercise the real production timeout");
        // Use the exact default production upload path, without starting its scheduler/UI.
        var constructor = BackgroundSyncManager.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        sync = constructor.newInstance();
        pending = new PendingItemsStore(EVENT);
        pending.appendItems(List.of(BackgroundSyncManagerTest.item("first"),
                BackgroundSyncManagerTest.item("second"), BackgroundSyncManagerTest.item("third")));
    }

    @AfterEach void tearDown() throws Exception {
        if (proxyName != null) proxyRequest("DELETE", "/proxies/" + proxyName, null);
        if (server != null) server.stop(0);
        if (serverThreads != null) {
            serverThreads.shutdownNow();
            assertTrue(serverThreads.awaitTermination(10, TimeUnit.SECONDS));
        }
        if (client != null) {
            client.setBasePath(previousBase);
            ApiHelper.INSTANCE.setCurrentApiKey(previousKey);
        }
        if (previousStorage == null) System.clearProperty("loppiskassan.base.dir");
        else System.setProperty("loppiskassan.base.dir", previousStorage);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            JSONArray items = new JSONObject(new String(exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8)).getJSONArray("items");
            String id = items.getJSONObject(0).getString("itemId");
            String currentFault = fault;
            if (currentFault.equals("outage") || (id.equals("first") && currentFault.equals("disconnect"))) {
                return; // Close without any HTTP response; nothing persisted.
            }
            if (id.equals("first") && currentFault.equals("503")) {
                exchange.sendResponseHeaders(503, -1);
                return;
            }
            JSONArray accepted = new JSONArray();
            JSONArray rejected = new JSONArray();
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                if (persisted.putIfAbsent(item.getString("itemId"), item) == null) accepted.put(item);
                else rejected.put(new JSONObject().put("item", item).put("reason", "duplicate item")
                        .put("errorCode", "SOLD_ITEM_ERROR_CODE_DUPLICATE_RECEIPT"));
            }
            if (id.equals("first") && currentFault.equals("timeout-after-write")) {
                Thread.sleep(6500); // Exceed the unmodified 5-second read timeout after persisting.
            }
            byte[] response = new JSONObject().put("acceptedItems", accepted).put("rejectedItems", rejected)
                    .toString().getBytes(StandardCharsets.UTF_8);
            if (currentFault.equals("latency-fragmentation")) Thread.sleep(250);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            if (currentFault.equals("latency-fragmentation")) {
                for (int start = 0; start < response.length; start += 8) {
                    exchange.getResponseBody().write(response, start, Math.min(8, response.length - start));
                    exchange.getResponseBody().flush();
                    Thread.sleep(3);
                }
            } else exchange.getResponseBody().write(response);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } finally {
            exchange.close();
        }
    }

    private void assertRecovered() throws Exception {
        assertTrue(pending.readPending().isEmpty());
        assertEquals(3, pending.readAll().size());
        assertEquals(450, pending.readAll().stream().mapToInt(V1SoldItem::getPrice).sum());
        assertEquals(java.util.Set.of("first", "second", "third"), persisted.keySet());
        assertEquals(450, persisted.values().stream().mapToInt(item -> item.getInt("price")).sum());
        assertTrue(new RejectedItemsStore(EVENT).readAll().isEmpty());
    }

    private BackgroundSyncManager.SyncResult firstFailsOthersSucceed(String mode) throws Exception {
        fault = mode;
        BackgroundSyncManager.SyncResult result = sync.syncOnce(EVENT);
        assertTrue(result.networkError());
        assertEquals(2, result.accepted());
        assertEquals(0, result.rejected());
        assertEquals(List.of("first"), pending.readPending().stream().map(V1SoldItem::getItemId).toList());
        return result;
    }

    @Test void interruptedConnectionDoesNotBlockOtherPurchases() throws Exception {
        firstFailsOthersSucceed("disconnect");
        assertEquals(2, persisted.size());
        fault = "none";
        assertEquals(1, sync.syncOnce(EVENT).accepted());
        assertRecovered();
    }

    @Test void lostResponseAfterServerWriteRetriesAsDuplicate() throws Exception {
        firstFailsOthersSucceed("timeout-after-write");
        assertEquals(3, persisted.size());
        fault = "none";
        assertEquals(1, sync.syncOnce(EVENT).duplicates());
        assertRecovered();
    }

    @Test void completeOutageRetainsAllPurchasesUntilRecovery() throws Exception {
        fault = "outage";
        assertTrue(sync.syncOnce(EVENT).networkError());
        assertEquals(3, pending.readPending().size());
        assertTrue(persisted.isEmpty());
        fault = "none";
        assertEquals(3, sync.syncOnce(EVENT).accepted());
        assertRecovered();
    }

    @Test void delayedFragmentedResponsesPreserveAllPurchases() throws Exception {
        fault = "latency-fragmentation";
        assertEquals(3, sync.syncOnce(EVENT).accepted());
        assertRecovered();
    }

    @Test void temporary503DoesNotBlockOtherPurchases() throws Exception {
        firstFailsOthersSucceed("503");
        fault = "none";
        assertEquals(1, sync.syncOnce(EVENT).accepted());
        assertRecovered();
    }

    private void proxyRequest(String method, String path, JSONObject body) throws Exception {
        try (var http = java.net.http.HttpClient.newHttpClient()) {
            var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(proxyAdmin + path))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .method(method, body == null ? java.net.http.HttpRequest.BodyPublishers.noBody()
                            : java.net.http.HttpRequest.BodyPublishers.ofString(body.toString())).build();
            var response = http.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            assertTrue(response.statusCode() >= 200 && response.statusCode() < 300,
                    "Toxiproxy " + method + " " + path + ": " + response.statusCode() + " " + response.body());
        }
    }

    private void toxic(String name, String type, JSONObject attributes) throws Exception {
        proxyRequest("POST", "/proxies/" + proxyName + "/toxics", new JSONObject()
                .put("name", name).put("type", type).put("stream", "downstream")
                .put("toxicity", 1.0).put("attributes", attributes));
    }

    @Test
    @EnabledIfSystemProperty(named = "loppiskassan.chaos.proxy.admin", matches = ".+")
    void proxyLatencyBandwidthAndFragmentation() throws Exception {
        toxic("delay", "latency", new JSONObject().put("latency", 300).put("jitter", 100));
        toxic("bandwidth", "bandwidth", new JSONObject().put("rate", 2));
        toxic("fragment", "slicer", new JSONObject().put("average_size", 16)
                .put("size_variation", 4).put("delay", 1000));
        assertEquals(3, sync.syncOnce(EVENT).accepted());
        assertRecovered();
    }

    @Test
    @EnabledIfSystemProperty(named = "loppiskassan.chaos.proxy.admin", matches = ".+")
    void proxyBlackholeAfterServerWritesRecoversWithoutDuplicates() throws Exception {
        toxic("blackhole", "timeout", new JSONObject().put("timeout", 0));
        assertTrue(sync.syncOnce(EVENT).networkError());
        assertEquals(3, pending.readPending().size());
        assertEquals(3, persisted.size());
        proxyRequest("DELETE", "/proxies/" + proxyName + "/toxics/blackhole", null);
        assertEquals(3, sync.syncOnce(EVENT).duplicates());
        assertRecovered();
    }
}
