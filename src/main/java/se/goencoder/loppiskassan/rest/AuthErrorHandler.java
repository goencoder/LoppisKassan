package se.goencoder.loppiskassan.rest;

import org.json.JSONArray;
import org.json.JSONObject;
import se.goencoder.iloppis.api.ApiKeyServiceApi;
import se.goencoder.iloppis.api.VendorServiceApi;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.V1Event;
import se.goencoder.iloppis.model.V1FilterVendorsResponse;
import se.goencoder.iloppis.model.V1GetApiKeyResponse;
import se.goencoder.iloppis.model.V1Pagination;
import se.goencoder.iloppis.model.V1RevenueSplit;
import se.goencoder.iloppis.model.V1Vendor;
import se.goencoder.iloppis.model.V1VendorFilter;
import se.goencoder.iloppis.model.VendorServiceFilterVendorsBody;
import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.config.ILoppisConfigurationStore;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.storage.OnlineEventCache;
import se.goencoder.loppiskassan.ui.EDT;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.ui.ProgressDialog;
import se.goencoder.loppiskassan.ui.dialogs.CashierCodeDialog;

import java.awt.Frame;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * Central handler for authorization errors (401/403).
 * Prompts the user for a new cashier code and refreshes credentials.
 */
public final class AuthErrorHandler {

    private static final Logger log = Logger.getLogger(AuthErrorHandler.class.getName());
    private static final int APPROVED_SELLERS_PAGE_SIZE = 500;
    private static final AtomicBoolean promptActive = new AtomicBoolean(false);

    private AuthErrorHandler() {}

    public static boolean isAuthError(ApiException e) {
        return e != null && isAuthStatus(e.getCode());
    }

    public static boolean isInvalidCashierCode(ApiException e) {
        if (e == null) {
            return false;
        }
        if (e.getCode() != 404) {
            return false;
        }
        String body = e.getResponseBody();
        if (body == null || body.isBlank()) {
            return false;
        }
        String normalized = body.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("event api key not found")
                || normalized.contains("api key not found")
                || normalized.contains("cashier code");
    }

    public static boolean isAuthStatus(int statusCode) {
        return statusCode == 401 || statusCode == 403;
    }

    public static boolean isPromptActive() {
        return promptActive.get();
    }

    public static boolean beginPrompt() {
        return promptActive.compareAndSet(false, true);
    }

    public static void endPrompt() {
        promptActive.set(false);
    }

    /**
     * Trigger re-authentication when an auth error is detected.
     */
    public static void handleAuthStatus(int statusCode) {
        if (!isAuthStatus(statusCode)) {
            return;
        }
        if (isPromptActive()) {
            return;
        }
        if (!AppModeManager.isILoppisMode()) {
            return;
        }

        String eventId = AppModeManager.getEventId();
        if (eventId == null || eventId.isBlank()) {
            return;
        }

        if (!beginPrompt()) {
            return;
        }

        se.goencoder.loppiskassan.storage.CachedOnlineEvent cached =
                OnlineEventCache.loadCachedEvent(eventId);
        boolean hadCachedCredentials = cached != null && cached.hasApiKey();
        OnlineEventCache.clearCachedCredentials(eventId);
        ILoppisConfigurationStore.setApiKey(null);
        ApiHelper.INSTANCE.clearCurrentApiKey();

        V1Event event = loadEventFromConfig();
        String eventName = event != null && event.getName() != null ? event.getName() : "";

        EDT.run(() -> {
            Frame owner = resolveOwner();
            CashierCodeDialog.Result result = CashierCodeDialog.showDialog(
                    owner,
                    LocalizationManager.tr("cashier_code.dialog.title"),
                    LocalizationManager.tr("api_key.invalid.dialog.message", eventName),
                    hadCachedCredentials
            );

            if (result == null || result.getCode().isBlank()) {
                endPrompt();
                return;
            }

            java.awt.Component progressParent = owner != null ? owner : new javax.swing.JPanel();
            ProgressDialog.runTask(
                    progressParent,
                    LocalizationManager.tr("cashier_code.dialog.progress_title"),
                    LocalizationManager.tr("cashier_code.dialog.progress_message"),
                    () -> {
                        refreshCredentials(eventId, event, result);
                        return null;
                    },
                    unused -> endPrompt(),
                    ex -> {
                        if (ex instanceof ApiException apiEx) {
                            if (isInvalidCashierCode(apiEx)) {
                                Popup.ERROR.showAndWait(
                                        LocalizationManager.tr("cashier_code.exchange_invalid.title"),
                                        LocalizationManager.tr("cashier_code.exchange_invalid.message"));
                            } else {
                                Popup.ERROR.showAndWait(LocalizationManager.tr("error.fetch_token.title"), apiEx);
                            }
                        } else {
                            Popup.ERROR.showAndWait(LocalizationManager.tr("error.generic.title"), ex.getMessage());
                        }
                        endPrompt();
                    }
            );
        });
    }

    private static void refreshCredentials(String eventId, V1Event event, CashierCodeDialog.Result result)
            throws ApiException {
        ApiKeyServiceApi apiKeyServiceApi = ApiHelper.INSTANCE.getApiKeyServiceApi();
        V1GetApiKeyResponse response = apiKeyServiceApi.apiKeyServiceGetApiKey(eventId, result.getCode(), null);
        if (response == null || response.getApiKey() == null || response.getApiKey().isBlank()) {
            throw new RuntimeException(LocalizationManager.tr("error.fetch_token.message", ""));
        }

        ApiHelper.INSTANCE.setCurrentApiKey(response.getApiKey());
        if (result.isRemember()) {
            ILoppisConfigurationStore.setApiKey(response.getApiKey());
        } else {
            ILoppisConfigurationStore.setApiKey(null);
        }

        refreshApprovedSellers(eventId);
        se.goencoder.loppiskassan.service.BackgroundSyncManager.getInstance().ensureRunning(eventId);

        V1RevenueSplit split = loadRevenueSplitFromConfig();
        if (event != null) {
            String cachedApiKey = result.isRemember() ? response.getApiKey() : "";
            String cachedSellers = result.isRemember() ? ILoppisConfigurationStore.getApprovedSellers() : "";
            OnlineEventCache.cacheEvent(event, cachedApiKey, cachedSellers, split);
        }
    }

    private static void refreshApprovedSellers(String eventId) throws ApiException {
        log.info("=== AuthErrorHandler refreshApprovedSellers START === event: " + eventId);
        
        VendorServiceApi api = ApiHelper.INSTANCE.getVendorServiceApi();
        Set<Integer> approvedSellers = new HashSet<>();
        String nextPageToken = "";
        int pageCount = 0;
        Integer totalApproved = null;
        int expectedPages = -1;
        String previousPageToken = null;
        
        // Use vendors:filter with status="approved" (same endpoint as Android/Frontend)
        do {
            pageCount++;
            
            V1VendorFilter filter = new V1VendorFilter();
            filter.setStatus("approved");
            
            V1Pagination pagination = new V1Pagination();
            pagination.setPageSize(APPROVED_SELLERS_PAGE_SIZE);
            if (!nextPageToken.isEmpty()) {
                pagination.setPageToken(nextPageToken);
            }
            
            VendorServiceFilterVendorsBody body = new VendorServiceFilterVendorsBody();
            body.setFilter(filter);
            body.setPagination(pagination);
            
            V1FilterVendorsResponse res = api.vendorServiceFilterVendors(eventId, body);
            if (res.getTotal() != null && totalApproved == null) {
                totalApproved = res.getTotal();
                expectedPages = (int) Math.ceil(totalApproved / (double) APPROVED_SELLERS_PAGE_SIZE);
            }
            
            if (res.getVendors() != null) {
                for (V1Vendor vendor : res.getVendors()) {
                    approvedSellers.add(vendor.getSellerNumber());
                }
            }
            if (res.getVendors() == null || res.getVendors().isEmpty()) {
                log.warning("No vendors returned on this page; stopping pagination.");
                break;
            }
            
            nextPageToken = res.getNextPageToken();
            if (nextPageToken != null && !nextPageToken.isEmpty()) {
                if (nextPageToken.equals(previousPageToken)) {
                    log.warning("Pagination token did not advance; stopping to avoid infinite loop.");
                    break;
                }
                if (expectedPages > 0 && pageCount >= expectedPages) {
                    log.warning("Reached expected page count from total; stopping pagination.");
                    break;
                }
                if (totalApproved != null && approvedSellers.size() >= totalApproved) {
                    log.info("Loaded all approved sellers; stopping pagination.");
                    break;
                }
            }
            previousPageToken = nextPageToken;
            
        } while (nextPageToken != null && !nextPageToken.isEmpty());
        
        log.info(String.format("=== AuthErrorHandler refreshApprovedSellers END === %d pages, %d APPROVED", 
            pageCount, approvedSellers.size()));
        
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("approvedSellers", new JSONArray(approvedSellers));
        ILoppisConfigurationStore.setApprovedSellers(jsonObject.toString());
    }

    private static V1Event loadEventFromConfig() {
        try {
            String eventJson = ILoppisConfigurationStore.getEventData();
            if (eventJson == null || eventJson.isBlank()) {
                return null;
            }
            return V1Event.fromJson(eventJson);
        } catch (IOException e) {
            return null;
        }
    }

    private static V1RevenueSplit loadRevenueSplitFromConfig() {
        try {
            String splitJson = ILoppisConfigurationStore.getRevenueSplit();
            if (splitJson == null || splitJson.isBlank()) {
                return null;
            }
            return V1RevenueSplit.fromJson(splitJson);
        } catch (IOException e) {
            return null;
        }
    }

    private static Frame resolveOwner() {
        for (Frame frame : Frame.getFrames()) {
            if (frame.isDisplayable()) {
                return frame;
            }
        }
        return null;
    }
}
