package se.goencoder.loppiskassan.tools;

import se.goencoder.iloppis.api.ApiKeyServiceApi;
import se.goencoder.iloppis.api.SoldItemsServiceApi;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.V1AggregateSoldItemsResponse;
import se.goencoder.iloppis.model.V1ExtendedAggregates;
import se.goencoder.iloppis.model.V1ListSoldItemsResponse;
import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.config.AppMode;
import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.config.ILoppisConfigurationStore;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.rest.FixedApiClient;
import se.goencoder.loppiskassan.service.BackgroundSyncManager;
import se.goencoder.loppiskassan.service.IloppisCashierStrategy;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.storage.LocalEventRepository;
import se.goencoder.loppiskassan.storage.RejectedItemsStore;
import se.goencoder.loppiskassan.utils.UlidGenerator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 * Manual load test that exercises the LoppisKassan iLoppis cashier flow:
 * local file write first, then background upload while new purchases continue.
 */
public final class CashierFlowLoadTestRunner {

    private static final int REMOTE_PAGE_SIZE = 500;
    private static final long POLL_INTERVAL_MS = 250;

    private record Config(
            String eventId,
            String apiBaseUrl,
            String apiKey,
            String cashierCode,
            int purchases,
            int durationSeconds,
            List<Integer> sellers,
            List<Integer> prices,
            boolean clearRemote,
            int waitTimeoutSeconds
    ) {
        static Config fromEnv() {
            String eventId = requireEnv("EVENT_ID");
            String apiBaseUrl = envOr("API_BASE_URL", "https://iloppis-staging.fly.dev");
            String apiKey = envOrNull("API_KEY");
            String cashierCode = envOrNull("CASHIER_CODE");
            int purchases = parseInt(envOr("PURCHASES", "100"));
            int durationSeconds = parseInt(envOr("DURATION_SECONDS", "50"));
            List<Integer> sellers = parseCsvInts(envOr("SELLERS", "2,36,1"));
            List<Integer> prices = parseCsvInts(envOr("PRICES", "50,100,150"));
            boolean clearRemote = parseBoolean(envOr("CLEAR_REMOTE", "false"));
            int waitTimeoutSeconds = parseInt(envOr("WAIT_TIMEOUT_SECONDS", "120"));

            if (purchases <= 0) {
                throw new IllegalArgumentException("PURCHASES must be positive");
            }
            if (durationSeconds <= 0) {
                throw new IllegalArgumentException("DURATION_SECONDS must be positive");
            }
            if (waitTimeoutSeconds <= 0) {
                throw new IllegalArgumentException("WAIT_TIMEOUT_SECONDS must be positive");
            }
            if (sellers.isEmpty()) {
                throw new IllegalArgumentException("SELLERS must contain at least one seller");
            }
            if (prices.isEmpty()) {
                throw new IllegalArgumentException("PRICES must contain at least one price");
            }
            if (prices.size() != 1 && prices.size() != sellers.size()) {
                throw new IllegalArgumentException("PRICES must contain either one value or one value per seller");
            }
            if (isBlank(apiKey) && isBlank(cashierCode)) {
                throw new IllegalArgumentException("API_KEY or CASHIER_CODE is required");
            }

            return new Config(
                    eventId,
                    apiBaseUrl,
                    apiKey,
                    cashierCode,
                    purchases,
                    durationSeconds,
                    sellers,
                    prices,
                    clearRemote,
                    waitTimeoutSeconds
            );
        }
    }

    private record LocalSummary(
            int purchases,
            int items,
            int totalPrice,
            int pendingRemaining,
            int rejectedCount
    ) {}

    private record RemoteSummary(
            int matchedPurchases,
            int matchedItems,
            int matchedTotalPrice
    ) {}

    private record WaitSummary(
            boolean completed,
            long waitedMs,
            int pendingRemaining
    ) {}

    public static void main(String[] args) throws Exception {
        Config cfg = Config.fromEnv();
        new CashierFlowLoadTestRunner().run(cfg);
    }

    private void run(Config cfg) throws Exception {
        Path isolatedHome = Files.createTempDirectory("loppiskassan-cashier-load-");
        System.setProperty("user.home", isolatedHome.toString());

        String apiKey = resolveApiKey(cfg);
        FixedApiClient remoteClient = createAuthenticatedClient(cfg.apiBaseUrl, apiKey);
        SoldItemsServiceApi soldItemsApi = new SoldItemsServiceApi(remoteClient);

        System.out.println("=== Cashier Flow Load Test ===");
        System.out.printf("Base URL:          %s%n", cfg.apiBaseUrl);
        System.out.printf("Event ID:          %s%n", cfg.eventId);
        System.out.printf("Purchases:         %d%n", cfg.purchases);
        System.out.printf("Duration:          %d s%n", cfg.durationSeconds);
        System.out.printf("Sellers:           %s%n", cfg.sellers);
        System.out.printf("Prices:            %s%n", cfg.prices);
        System.out.printf("Clear remote:      %s%n", cfg.clearRemote);
        System.out.printf("Isolated user.home:%s%n", isolatedHome);

        V1ExtendedAggregates beforeAggregate = fetchAggregate(soldItemsApi, cfg.eventId);
        printAggregate("Remote aggregate before", beforeAggregate);

        if (cfg.clearRemote) {
            int deleted = clearRemoteSoldItems(soldItemsApi, cfg.eventId);
            System.out.printf("Cleared remote sold items: %d%n", deleted);
            V1ExtendedAggregates afterClear = fetchAggregate(soldItemsApi, cfg.eventId);
            printAggregate("Remote aggregate after clear", afterClear);
        }

        AppModeManager.setMode(AppMode.ILOPPIS);
        ILoppisConfigurationStore.setEventId(cfg.eventId);
        ILoppisConfigurationStore.setApiBaseUrl(cfg.apiBaseUrl);
        ILoppisConfigurationStore.setApiKey(apiKey);
        ApiHelper.INSTANCE.setCurrentApiKey(apiKey);
        LocalEventRepository.ensureEventStorage(cfg.eventId);

        BackgroundSyncManager.getInstance().stop();
        BackgroundSyncManager.getInstance().ensureRunning(cfg.eventId);

        Path pendingPath = LocalEventPaths.getPendingItemsPath(cfg.eventId);
        System.out.printf("Local pending file: %s%n", pendingPath);

        IloppisCashierStrategy strategy = new IloppisCashierStrategy();
        Set<String> expectedItemIds = new HashSet<>();
        Set<String> expectedPurchaseIds = new HashSet<>();
        List<Long> persistLatenciesMs = new ArrayList<>();

        int totalItems = 0;
        int totalPrice = 0;
        long testStartNanos = System.nanoTime();
        OffsetDateTime remoteStartTime = OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(2);
        long intervalNanos = TimeUnit.SECONDS.toNanos(cfg.durationSeconds) / cfg.purchases;

        for (int purchaseIndex = 0; purchaseIndex < cfg.purchases; purchaseIndex++) {
            String purchaseId = UlidGenerator.generate();
            expectedPurchaseIds.add(purchaseId);
            V1PaymentMethod paymentMethod = (purchaseIndex % 2 == 0) ? V1PaymentMethod.Kontant : V1PaymentMethod.Swish;
            LocalDateTime soldTime = LocalDateTime.now();

            List<V1SoldItem> batch = new ArrayList<>();
            for (int itemIndex = 0; itemIndex < cfg.sellers.size(); itemIndex++) {
                int seller = cfg.sellers.get(itemIndex);
                int price = cfg.prices.size() == 1 ? cfg.prices.getFirst() : cfg.prices.get(itemIndex);
                String itemId = UlidGenerator.generate();
                batch.add(new V1SoldItem(
                        purchaseId,
                        itemId,
                        soldTime,
                        seller,
                        price,
                        null,
                        paymentMethod,
                        false
                ));
                expectedItemIds.add(itemId);
                totalItems++;
                totalPrice += price;
            }

            long persistStart = System.nanoTime();
            strategy.persistItems(batch, purchaseId, paymentMethod, soldTime);
            long persistMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - persistStart);
            persistLatenciesMs.add(persistMs);

            long nextTarget = testStartNanos + ((long) purchaseIndex + 1L) * intervalNanos;
            long sleepNanos = nextTarget - System.nanoTime();
            if (sleepNanos > 0) {
                LockSupport.parkNanos(sleepNanos);
            }
        }

        OffsetDateTime remoteEndTime = OffsetDateTime.now(ZoneOffset.UTC).plusSeconds(2);
        WaitSummary waitSummary = waitForPendingUploads(cfg.eventId, cfg.waitTimeoutSeconds);

        List<V1SoldItem> localItems = JsonlHelper.readItems(pendingPath);
        int pendingRemaining = (int) localItems.stream().filter(item -> !item.isUploaded()).count();
        int rejectedCount = new RejectedItemsStore(cfg.eventId).count();
        LocalSummary localSummary = new LocalSummary(
                cfg.purchases,
                totalItems,
                totalPrice,
                pendingRemaining,
                rejectedCount
        );

        List<se.goencoder.iloppis.model.V1SoldItem> remoteItems = listAllRemoteItems(soldItemsApi, cfg.eventId);
        RemoteSummary remoteSummary = summarizeRemote(remoteItems, expectedItemIds, expectedPurchaseIds, remoteStartTime, remoteEndTime);
        V1ExtendedAggregates afterAggregate = fetchAggregate(soldItemsApi, cfg.eventId);

        printLatencySummary(persistLatenciesMs);
        printLocalSummary(localSummary, waitSummary);
        printRemoteSummary(remoteSummary, afterAggregate);

        BackgroundSyncManager.getInstance().stop();

        if (remoteSummary.matchedItems != localSummary.items
                || remoteSummary.matchedPurchases != localSummary.purchases
                || remoteSummary.matchedTotalPrice != localSummary.totalPrice
                || localSummary.pendingRemaining != 0
                || localSummary.rejectedCount != 0) {
            throw new IllegalStateException("Load test verification failed");
        }
    }

    private String resolveApiKey(Config cfg) throws ApiException {
        if (!isBlank(cfg.apiKey)) {
            return cfg.apiKey;
        }
        FixedApiClient client = new FixedApiClient();
        client.setBasePath(cfg.apiBaseUrl);
        ApiKeyServiceApi api = new ApiKeyServiceApi(client);
        return api.apiKeyServiceGetApiKey(cfg.eventId, cfg.cashierCode, null).getApiKey();
    }

    private FixedApiClient createAuthenticatedClient(String apiBaseUrl, String apiKey) {
        FixedApiClient client = new FixedApiClient();
        client.setBasePath(apiBaseUrl);
        client.addDefaultHeader("Authorization", "Bearer " + apiKey);
        client.setUserAgent("LoppisKassan/3.0.0 cashier-flow-load-test");
        return client;
    }

    private int clearRemoteSoldItems(SoldItemsServiceApi soldItemsApi, String eventId) throws ApiException {
        int deleted = 0;
        while (true) {
            V1ListSoldItemsResponse page = soldItemsApi.soldItemsServiceListSoldItems(
                    eventId,
                    null,
                    null,
                    null,
                    null,
                    Boolean.TRUE,
                    REMOTE_PAGE_SIZE,
                    "",
                    "",
                    Boolean.FALSE
            );
            List<se.goencoder.iloppis.model.V1SoldItem> items = page.getItems();
            if (items == null || items.isEmpty()) {
                return deleted;
            }
            for (se.goencoder.iloppis.model.V1SoldItem item : items) {
                soldItemsApi.soldItemsServiceDeleteSoldItem(eventId, item.getItemId());
                deleted++;
            }
        }
    }

    private WaitSummary waitForPendingUploads(String eventId, int timeoutSeconds) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
        BackgroundSyncManager syncManager = BackgroundSyncManager.getInstance();
        while (System.currentTimeMillis() < deadline) {
            int pending = syncManager.getPendingCount();
            if (pending == 0) {
                long waitedMs = TimeUnit.SECONDS.toMillis(timeoutSeconds) - (deadline - System.currentTimeMillis());
                return new WaitSummary(true, waitedMs, 0);
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        return new WaitSummary(false, TimeUnit.SECONDS.toMillis(timeoutSeconds), syncManager.getPendingCount());
    }

    private List<se.goencoder.iloppis.model.V1SoldItem> listAllRemoteItems(SoldItemsServiceApi soldItemsApi, String eventId) throws ApiException {
        List<se.goencoder.iloppis.model.V1SoldItem> items = new ArrayList<>();
        String pageToken = "";
        while (true) {
            V1ListSoldItemsResponse page = soldItemsApi.soldItemsServiceListSoldItems(
                    eventId,
                    null,
                    null,
                    null,
                    null,
                    Boolean.TRUE,
                    REMOTE_PAGE_SIZE,
                    pageToken,
                    "",
                    Boolean.FALSE
            );
            if (page.getItems() != null) {
                items.addAll(page.getItems());
            }
            if (page.getNextPageToken() == null || page.getNextPageToken().isBlank()) {
                return items;
            }
            pageToken = page.getNextPageToken();
        }
    }

    private RemoteSummary summarizeRemote(List<se.goencoder.iloppis.model.V1SoldItem> remoteItems,
                                          Set<String> expectedItemIds,
                                          Set<String> expectedPurchaseIds,
                                          OffsetDateTime startTime,
                                          OffsetDateTime endTime) {
        int itemCount = 0;
        int totalPrice = 0;
        Set<String> purchaseIds = new HashSet<>();
        for (se.goencoder.iloppis.model.V1SoldItem item : remoteItems) {
            if (item.getItemId() == null || !expectedItemIds.contains(item.getItemId())) {
                continue;
            }
            if (item.getPurchaseId() == null || !expectedPurchaseIds.contains(item.getPurchaseId())) {
                continue;
            }
            if (item.getSoldTime() != null && (item.getSoldTime().isBefore(startTime) || item.getSoldTime().isAfter(endTime))) {
                continue;
            }
            itemCount++;
            totalPrice += item.getPrice() != null ? item.getPrice() : 0;
            purchaseIds.add(item.getPurchaseId());
        }
        return new RemoteSummary(purchaseIds.size(), itemCount, totalPrice);
    }

    private V1ExtendedAggregates fetchAggregate(SoldItemsServiceApi soldItemsApi, String eventId) throws ApiException {
        V1AggregateSoldItemsResponse response = soldItemsApi.soldItemsServiceAggregateSoldItems(eventId);
        return response != null ? response.getAggregates() : null;
    }

    private void printAggregate(String label, V1ExtendedAggregates aggregate) {
        if (aggregate == null) {
            System.out.printf("%s: <none>%n", label);
            return;
        }
        System.out.printf(
                "%s: purchases=%d items=%d total=%d%n",
                label,
                valueOrZero(aggregate.getTotalPurchases()),
                valueOrZero(aggregate.getTotalItems()),
                valueOrZero(aggregate.getTotalPrice())
        );
    }

    private void printLatencySummary(List<Long> persistLatenciesMs) {
        List<Long> sorted = new ArrayList<>(persistLatenciesMs);
        sorted.sort(Long::compareTo);
        long max = sorted.getLast();
        long sum = 0;
        for (Long value : sorted) {
            sum += value;
        }
        long avg = sorted.isEmpty() ? 0 : sum / sorted.size();
        long p95 = sorted.get((int) Math.max(0, Math.ceil(sorted.size() * 0.95) - 1));

        System.out.println("Latency summary (persistItems -> local file write):");
        System.out.printf("  avg=%d ms, p95=%d ms, max=%d ms%n", avg, p95, max);
    }

    private void printLocalSummary(LocalSummary localSummary, WaitSummary waitSummary) {
        System.out.println("Local summary:");
        System.out.printf("  purchases=%d items=%d total=%d%n",
                localSummary.purchases,
                localSummary.items,
                localSummary.totalPrice);
        System.out.printf("  uploadWaitCompleted=%s waitedMs=%d pendingRemaining=%d rejected=%d%n",
                waitSummary.completed,
                waitSummary.waitedMs,
                localSummary.pendingRemaining,
                localSummary.rejectedCount);
    }

    private void printRemoteSummary(RemoteSummary remoteSummary, V1ExtendedAggregates aggregate) {
        System.out.println("Remote summary:");
        System.out.printf("  matchedPurchases=%d matchedItems=%d matchedTotal=%d%n",
                remoteSummary.matchedPurchases,
                remoteSummary.matchedItems,
                remoteSummary.matchedTotalPrice);
        printAggregate("  remote aggregate now", aggregate);
    }

    private static int valueOrZero(Integer value) {
        return value != null ? value : 0;
    }

    private static String envOr(String key, String defaultValue) {
        String value = System.getenv(key);
        return isBlank(value) ? defaultValue : value;
    }

    private static String envOrNull(String key) {
        return System.getenv(key);
    }

    private static String requireEnv(String key) {
        String value = System.getenv(key);
        if (isBlank(value)) {
            throw new IllegalArgumentException("Missing required env var: " + key);
        }
        return value;
    }

    private static boolean parseBoolean(String value) {
        return "true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value);
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid integer: " + value, ex);
        }
    }

    private static List<Integer> parseCsvInts(String value) {
        List<Integer> result = new ArrayList<>();
        for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(parseInt(trimmed));
            }
        }
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
