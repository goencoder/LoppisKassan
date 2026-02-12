package se.goencoder.loppiskassan.tools;

import se.goencoder.iloppis.api.*;
import se.goencoder.iloppis.model.*;
import se.goencoder.loppiskassan.rest.FixedApiClient;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Setup tool for creating complete market infrastructure from scratch:
 * 1. Login as platform owner + market owner (via MailHog magic links)
 * 2. Create & approve draft market  
 * 3. Create event
 * 4. Create vendors (configurable count)
 * 5. Approve all vendors
 * 6. Create cashier API key
 * 
 * Run via: make load-test ENV=load-test-setup.env
 */
public final class SetupRunner {
    
    private record Config(
            String apiBaseUrl,
            String mailhogUrl,
            String platformOwnerEmail,
            String marketOwnerEmail,
            String marketName,
            String eventName,
            int vendorCount
    ) {
        static Config fromEnv() {
            return new Config(
                    envOr("API_BASE_URL", "http://127.0.0.1:8080"),
                    envOr("MAILHOG_URL", "http://localhost:8025"),
                    envOr("PLATFORM_OWNER_EMAIL", "goran@goencoder.se"),
                    envOr("MARKET_OWNER_EMAIL", "peter.karlsson@sillstugan.se"),
                    envOr("MARKET_NAME", "Load Test Market"),
                    envOr("EVENT_NAME", "Load Test Event"),
                    parseInt(envOr("VENDOR_COUNT", "100"))
            );
        }
    }
    
    private record SetupResult(
            String marketId, 
            String eventId, 
            String cashierAlias, 
            String cashierApiKey
    ) {}
    
    public static void main(String[] args) throws Exception {
        Config cfg = Config.fromEnv();
        SetupRunner runner = new SetupRunner();
        SetupResult result = runner.run(cfg);
        
        System.out.println("\n══════════════════════════════════════");
        System.out.println("✓ SETUP COMPLETE!");
        System.out.println("══════════════════════════════════════");
        System.out.printf("Market ID:     %s%n", result.marketId);
        System.out.printf("Event ID:      %s%n", result.eventId);
        System.out.printf("Cashier Alias: %s%n", result.cashierAlias);
        System.out.printf("API Key:       %s%n", result.cashierApiKey);
        System.out.println("\nAdd to your load test ENV file:");
        System.out.printf("EVENT_ID=%s%n", result.eventId);
        System.out.printf("API_KEY=%s%n", result.cashierApiKey);
        System.out.println("══════════════════════════════════════\n");
    }
    
    private SetupResult run(Config cfg) throws Exception {
        MailHogClient mailhog = new MailHogClient(cfg.mailhogUrl);
        
        System.out.println("═══ iLoppis Market Setup ═══");
        System.out.printf("API:     %s%n", cfg.apiBaseUrl);
        System.out.printf("MailHog: %s%n", cfg.mailhogUrl);
        System.out.printf("Vendors: %d%n", cfg.vendorCount);
        System.out.println();
        
        // Step 1: Login platform owner
        System.out.println("[1/7] Logging in as platform owner: " + cfg.platformOwnerEmail);
        String platformApiKey = loginWithMagicLink(cfg, mailhog, cfg.platformOwnerEmail);
        System.out.println("      ✓ Platform owner logged in");
        
        // Step 2: Login market owner
        System.out.println("[2/7] Logging in as market owner: " + cfg.marketOwnerEmail);
        String ownerApiKey = loginWithMagicLink(cfg, mailhog, cfg.marketOwnerEmail);
        System.out.println("      ✓ Market owner logged in");
        
        // Step 3: Create draft market
        System.out.println("[3/7] Creating draft market: " + cfg.marketName);
        String marketId = createDraftMarket(cfg, ownerApiKey);
        System.out.printf("      ✓ Market created (ID: %s)%n", marketId);
        
        // Step 4: Approve market
        System.out.println("[4/7] Approving market (as platform owner)");
        approveMarket(cfg, platformApiKey, marketId);
        System.out.println("      ✓ Market approved");
        
        // Step 5: Create event
        System.out.println("[5/7] Creating event: " + cfg.eventName);
        String eventId = createEvent(cfg, ownerApiKey, marketId);
        System.out.printf("      ✓ Event created (ID: %s)%n", eventId);
        
        // Step 6: Create and approve vendors
        System.out.printf("[6/7] Creating and approving %d vendors%n", cfg.vendorCount);
        createAndApproveVendors(cfg, ownerApiKey, eventId);
        System.out.printf("      ✓ All %d vendors created and approved%n", cfg.vendorCount);
        
        // Step 7: Create cashier API key
        System.out.println("[7/7] Creating cashier API key");
        V1CreateApiKeyResponse cashierKey = createCashierApiKey(cfg, ownerApiKey, eventId);
        System.out.printf("      ✓ Cashier key created (alias: %s)%n", cashierKey.getAlias());
        
        return new SetupResult(marketId, eventId, cashierKey.getAlias(), cashierKey.getApiKey());
    }
    
    private String loginWithMagicLink(Config cfg, MailHogClient mailhog, String email) throws Exception {
        // Trigger magic link
        FixedApiClient client = new FixedApiClient();
        client.setBasePath(cfg.apiBaseUrl);
        LoginServiceApi loginApi = new LoginServiceApi(client);
        
        V1LoginRequest loginBody = new V1LoginRequest();
        loginBody.setEmail(email);
        loginApi.loginServiceLogin(loginBody);
        
        // Poll MailHog for API key (30 attempts × 1s = 30s timeout)
        return mailhog.pollForMagicLink(email, 30, 1000);
    }
    
    private String createDraftMarket(Config cfg, String apiKey) throws Exception {
        FixedApiClient client = createAuthenticatedClient(cfg, apiKey);
        DraftMarketServiceApi draftMarketApi = new DraftMarketServiceApi(client);
        
        V1Market market = new V1Market();
        market.setName(cfg.marketName);
        market.setDescription("Automated load test market - " + System.currentTimeMillis());
        market.setMaxTicketsPerPerson(5);
        market.setOwnerEmail(cfg.marketOwnerEmail);
        
        V1RevenueSplit revenueSplit = new V1RevenueSplit();
        revenueSplit.setVendorPercentage(85.0f);
        revenueSplit.setMarketOwnerPercentage(10.0f);
        revenueSplit.setPlatformProviderPercentage(5.0f);
        revenueSplit.setCharityPercentage(0.0f);
        market.setRevenueSplit(revenueSplit);
        
        V1CreateMarketRequest createBody = new V1CreateMarketRequest();
        createBody.setMarket(market);
        
        V1CreateMarketResponse response = draftMarketApi.draftMarketServiceCreateMarket(createBody);
        return response.getMarket().getId();
    }
    
    private void approveMarket(Config cfg, String platformApiKey, String marketId) throws Exception {
        FixedApiClient client = createAuthenticatedClient(cfg, platformApiKey);
        DraftMarketServiceApi draftMarketApi = new DraftMarketServiceApi(client);
        
        // ApproveMarket takes marketId and an empty body object
        draftMarketApi.draftMarketServiceApproveMarket(marketId, new Object());
    }
    
    private String createEvent(Config cfg, String ownerApiKey, String marketId) throws Exception {
        FixedApiClient client = createAuthenticatedClient(cfg, ownerApiKey);
        EventServiceApi eventApi = new EventServiceApi(client);
        
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime tomorrow = now.plusDays(1);
        
        V1Event event = new V1Event();
        event.setName(cfg.eventName);
        event.setMarketId(marketId);
        event.setStartTime(now);
        event.setEndTime(tomorrow);
        event.setVendorApplicationStartTime(now);
        event.setAddressStreet("Test Street 1");
        event.setAddressCity("Test City");
        event.setAddressZip("12345");
        event.setDescription("Automated load test event");
        
        V1CreateEventRequest createBody = new V1CreateEventRequest();
        createBody.setEvent(event);
        
        V1CreateEventResponse response = eventApi.eventServiceCreateEvent(createBody);
        return response.getEvent().getId();
    }
    
    private void createAndApproveVendors(Config cfg, String ownerApiKey, String eventId) throws Exception {
        FixedApiClient client = createAuthenticatedClient(cfg, ownerApiKey);
        VendorServiceApi vendorApi = new VendorServiceApi(client);
        
        List<String> vendorIds = new ArrayList<>();
        
        // Create vendors
        System.out.print("      Creating vendors: ");
        for (int i = 1; i <= cfg.vendorCount; i++) {
            V1VendorApplicant applicant = new V1VendorApplicant();
            applicant.setEmail(String.format("vendor%d@loadtest.local", i));
            applicant.setFirstName("Vendor" + i);
            applicant.setLastName("LoadTest");
            
            VendorServiceCreateVendorBody createBody = new VendorServiceCreateVendorBody();
            createBody.setSellerNumber(i);
            createBody.setFreeText("");
            createBody.setApplicant(applicant);
            
            V1VendorResponse response = vendorApi.vendorServiceCreateVendor(eventId, createBody);
            vendorIds.add(response.getVendor().getVendorId());
            
            if (i % 20 == 0 || i == cfg.vendorCount) {
                System.out.printf("%d/%d ", i, cfg.vendorCount);
            }
        }
        System.out.println();
        
        // Approve all vendors
        System.out.print("      Approving vendors: ");
        for (int i = 0; i < vendorIds.size(); i++) {
            V1Vendor vendor = new V1Vendor();
            vendor.setVendorId(vendorIds.get(i));
            vendor.setSellerNumber(i + 1); // Seller numbers start at 1
            vendor.setStatus("approved");
            
            VendorServiceUpdateVendorBody updateBody = new VendorServiceUpdateVendorBody();
            updateBody.setVendor(vendor);
            
            vendorApi.vendorServiceUpdateVendor(eventId, vendorIds.get(i), updateBody);
            
            if ((i + 1) % 20 == 0 || (i + 1) == vendorIds.size()) {
                System.out.printf("%d/%d ", i + 1, cfg.vendorCount);
            }
        }
        System.out.println();
    }
    
    private V1CreateApiKeyResponse createCashierApiKey(Config cfg, String ownerApiKey, String eventId) throws Exception {
        FixedApiClient client = createAuthenticatedClient(cfg, ownerApiKey);
        ApiKeyServiceApi apiKeyApi = new ApiKeyServiceApi(client);
        
        ApiKeyServiceCreateApiKeyBody createBody = new ApiKeyServiceCreateApiKeyBody();
        createBody.setType(V1ApiKeyType.WEB_CASHIER);
        createBody.setTags(List.of());
        
        return apiKeyApi.apiKeyServiceCreateApiKey(eventId, createBody);
    }
    
    private FixedApiClient createAuthenticatedClient(Config cfg, String apiKey) {
        FixedApiClient client = new FixedApiClient();
        client.setBasePath(cfg.apiBaseUrl);
        client.addDefaultHeader("Authorization", "Bearer " + apiKey);
        return client;
    }
    
    // Utility methods
    private static String envOr(String key, String defaultValue) {
        String val = System.getenv(key);
        return (val == null || val.isBlank()) ? defaultValue : val;
    }
    
    private static int parseInt(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid integer: " + raw, ex);
        }
    }
}
