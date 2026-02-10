package se.goencoder.loppiskassan.tools;

import se.goencoder.iloppis.api.ApiKeyServiceApi;
import se.goencoder.iloppis.api.SoldItemsServiceApi;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.SoldItemsServiceCreateSoldItemsBody;
import se.goencoder.iloppis.model.V1CreateSoldItemsResponse;
import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.config.AppMode;
import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.config.LocalConfigurationStore;
import se.goencoder.loppiskassan.rest.FixedApiClient;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.storage.LocalEventRepository;
import se.goencoder.loppiskassan.utils.SoldItemUtils;
import se.goencoder.loppiskassan.utils.UlidGenerator;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manual load generator for local or iLoppis modes.
 * Not part of automated test suite; invoke via make load-test with an env file.
 */
public final class LoadTestRunner {

    private enum Mode { LOCAL, ILOPPIS }

    private record Config(
            Mode mode,
            String eventId,
            String apiBaseUrl,
            String apiKey,
            String cashierCode,
            int sellerCount,
            int minPrice,
            int maxPrice,
            int minItemsPerPurchase,
            int maxItemsPerPurchase,
            long targetTotal
    ) {
        static Config fromEnv() {
            Mode mode = Mode.valueOf(envOr("MODE", "LOCAL").toUpperCase(Locale.ROOT));
            String eventId = requireEnv("EVENT_ID");
            String apiBaseUrl = envOr("API_BASE_URL", "http://127.0.0.1:8080");
            String apiKey = envOrNull("API_KEY");
            String cashierCode = envOrNull("CASHIER_CODE");

            int sellerCount = parseInt(envOr("SELLER_COUNT", "100"));
            int minPrice = parseInt(envOr("MIN_PRICE", "10"));
            int maxPrice = parseInt(envOr("MAX_PRICE", "1000"));
            int minItems = parseInt(envOr("MIN_ITEMS_PER_PURCHASE", "10"));
            int maxItems = parseInt(envOr("MAX_ITEMS_PER_PURCHASE", "20"));
            long targetTotal = parseLong(envOr("TARGET_TOTAL", "500000"));

            if (minPrice <= 0 || maxPrice < minPrice) {
                throw new IllegalArgumentException("Invalid price range");
            }
            if (minItems <= 0 || maxItems < minItems) {
                throw new IllegalArgumentException("Invalid item count range");
            }
            if (sellerCount <= 0) {
                throw new IllegalArgumentException("Seller count must be positive");
            }
            if (targetTotal <= 0) {
                throw new IllegalArgumentException("Target total must be positive");
            }
            if (mode == Mode.ILOPPIS && (isBlank(apiKey) && isBlank(cashierCode))) {
                throw new IllegalArgumentException("ILOPPIS mode requires API_KEY or CASHIER_CODE");
            }

            return new Config(
                    mode,
                    eventId,
                    apiBaseUrl,
                    apiKey,
                    cashierCode,
                    sellerCount,
                    minPrice,
                    maxPrice,
                    minItems,
                    maxItems,
                    targetTotal
            );
        }
    }

    public static void main(String[] args) throws Exception {
        Config cfg = Config.fromEnv();
        LoadTestRunner runner = new LoadTestRunner();
        runner.run(cfg);
    }

    private void run(Config cfg) throws Exception {
        LoadSink sink = cfg.mode == Mode.LOCAL ? new LocalSink(cfg) : new IloppisSink(cfg);
        try {
            long total = 0;
            int purchases = 0;
            int items = 0;
            Random random = ThreadLocalRandom.current();
            while (total < cfg.targetTotal) {
                int itemCount = nextItemCount(cfg, random);
                String purchaseId = UlidGenerator.generate();
                List<V1SoldItem> batch = new ArrayList<>(itemCount);
                for (int i = 0; i < itemCount && total < cfg.targetTotal; i++) {
                    int price = nextPrice(cfg, random);
                    int seller = 1 + ((purchases * itemCount + i) % cfg.sellerCount);
                    V1PaymentMethod payment = random.nextBoolean() ? V1PaymentMethod.Kontant : V1PaymentMethod.Swish;
                    String itemId = UlidGenerator.generate();
                    LocalDateTime soldTime = LocalDateTime.now();
                    batch.add(new V1SoldItem(
                            purchaseId,
                            itemId,
                            soldTime,
                            seller,
                            price,
                            null,
                            payment,
                            false
                    ));
                    total += price;
                }
                sink.submit(batch);
                purchases++;
                items += batch.size();
                if (purchases % 10 == 0 || total >= cfg.targetTotal) {
                    System.out.printf("Progress: purchases=%d items=%d total=%d/%d%n", purchases, items, total, cfg.targetTotal);
                }
            }
            System.out.printf("Done: purchases=%d items=%d total=%d%n", purchases, items, total);
        } finally {
            sink.close();
        }
    }

    private int nextItemCount(Config cfg, Random random) {
        if (cfg.minItemsPerPurchase == cfg.maxItemsPerPurchase) {
            return cfg.minItemsPerPurchase;
        }
        return cfg.minItemsPerPurchase + random.nextInt(cfg.maxItemsPerPurchase - cfg.minItemsPerPurchase + 1);
    }

    private int nextPrice(Config cfg, Random random) {
        if (cfg.minPrice == cfg.maxPrice) {
            return cfg.minPrice;
        }
        return cfg.minPrice + random.nextInt(cfg.maxPrice - cfg.minPrice + 1);
    }

    private interface LoadSink {
        void submit(List<V1SoldItem> items) throws Exception;
        default void close() throws Exception {}
    }

    private static final class LocalSink implements LoadSink {
        private final String eventId;
        private final Path pendingPath;

        LocalSink(Config cfg) throws Exception {
            this.eventId = cfg.eventId;
            AppModeManager.setMode(AppMode.LOCAL);
            LocalConfigurationStore.setEventId(eventId);
            LocalEventRepository.ensureEventStorage(eventId);
            this.pendingPath = LocalEventPaths.getPendingItemsPath(eventId);
            System.out.printf("Local mode: writing to %s%n", pendingPath);
        }

        @Override
        public void submit(List<V1SoldItem> items) throws Exception {
            JsonlHelper.appendItems(pendingPath, items);
        }
    }

    private static final class IloppisSink implements LoadSink {
        private final String eventId;
        private final SoldItemsServiceApi soldItemsApi;

        IloppisSink(Config cfg) throws Exception {
            this.eventId = cfg.eventId;
            FixedApiClient client = new FixedApiClient();
            client.setBasePath(cfg.apiBaseUrl);
            client.setUserAgent("LoppisKassan/2.0.0 load-test");

            String token = cfg.apiKey;
            if (isBlank(token)) {
                ApiKeyServiceApi apiKeyApi = new ApiKeyServiceApi(client);
                token = apiKeyApi.apiKeyServiceGetApiKey(cfg.eventId, cfg.cashierCode, null).getApiKey();
            }
            client.addDefaultHeader("Authorization", "Bearer " + token);
            this.soldItemsApi = new SoldItemsServiceApi(client);
            System.out.printf("iLoppis mode: base=%s event=%s%n", cfg.apiBaseUrl, cfg.eventId);
        }

        @Override
        public void submit(List<V1SoldItem> items) throws Exception {
            SoldItemsServiceCreateSoldItemsBody body = new SoldItemsServiceCreateSoldItemsBody();
            for (V1SoldItem item : items) {
                se.goencoder.iloppis.model.V1SoldItem apiItem = new se.goencoder.iloppis.model.V1SoldItem(item.getItemId(), eventId, null);
                apiItem.setPurchaseId(item.getPurchaseId());
                apiItem.setSeller(item.getSeller());
                apiItem.setPrice(item.getPrice());
                apiItem.setPaymentMethod(
                        item.getPaymentMethod() == V1PaymentMethod.Kontant
                                ? se.goencoder.iloppis.model.V1PaymentMethod.KONTANT
                                : se.goencoder.iloppis.model.V1PaymentMethod.SWISH
                );
                apiItem.setSoldTime(item.getSoldTime().atOffset(ZoneOffset.UTC));
                body.addItemsItem(apiItem);
            }
            V1CreateSoldItemsResponse response = soldItemsApi.soldItemsServiceCreateSoldItems(eventId, body);
            int rejected = response.getRejectedItems() == null ? 0 : response.getRejectedItems().size();
            if (rejected > 0) {
                System.out.printf("Warning: %d items rejected for purchase %s%n", rejected, items.getFirst().getPurchaseId());
            }
        }
    }

    private static String envOr(String key, String defaultValue) {
        String val = System.getenv(key);
        return isBlank(val) ? defaultValue : val;
    }

    private static String envOrNull(String key) {
        return System.getenv(key);
    }

    private static String requireEnv(String key) {
        String val = System.getenv(key);
        if (isBlank(val)) {
            throw new IllegalArgumentException("Missing required env var: " + key);
        }
        return val;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid integer: " + raw, ex);
        }
    }

    private static long parseLong(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid long: " + raw, ex);
        }
    }
}
