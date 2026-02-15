package se.goencoder.loppiskassan.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import se.goencoder.iloppis.api.VendorServiceApi;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.V1FilterVendorsResponse;
import se.goencoder.iloppis.model.V1Pagination;
import se.goencoder.iloppis.model.V1Vendor;
import se.goencoder.iloppis.model.V1VendorFilter;
import se.goencoder.iloppis.model.VendorServiceFilterVendorsBody;
import se.goencoder.loppiskassan.config.ILoppisConfigurationStore;
import se.goencoder.loppiskassan.rest.ApiHelper;

import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Helper for refreshing approved vendors list from API.
 * 
 * Uses the vendors:filter endpoint (same as Android VendorRepository and Frontend)
 * with status="approved" to get pre-filtered results from the backend.
 * 
 * This avoids issues with the vendorServiceListVendors endpoint which can
 * return inconsistent data.
 */
public class VendorRefreshHelper {
    
    private static final Logger log = Logger.getLogger(VendorRefreshHelper.class.getName());
    private static final int PAGE_SIZE = 100;
    
    /**
     * Refresh approved sellers from API and update configuration store.
     * 
     * Uses the vendors:filter endpoint with status="approved" (same as Android app).
     * Paginates through ALL pages to ensure complete seller list.
     * 
     * @param eventId the event ID to fetch vendors for
     * @return true if refresh succeeded, false on error
     */
    public static boolean refreshApprovedSellers(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            log.warning("Cannot refresh sellers: eventId is null or blank");
            return false;
        }
        
        try {
            log.info("=== VendorRefresh START === event: " + eventId);
            
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
                log.info(String.format("Fetching page %d (filter: status=approved), pageToken='%s'", pageCount, nextPageToken));
                
                V1VendorFilter filter = new V1VendorFilter();
                filter.setStatus("approved");
                
                V1Pagination pagination = new V1Pagination();
                pagination.setPageSize(PAGE_SIZE);
                if (!nextPageToken.isEmpty()) {
                    pagination.setPageToken(nextPageToken);
                }
                
                VendorServiceFilterVendorsBody body = new VendorServiceFilterVendorsBody();
                body.setFilter(filter);
                body.setPagination(pagination);
                
                V1FilterVendorsResponse response = api.vendorServiceFilterVendors(eventId, body);
                if (response.getTotal() != null && totalApproved == null) {
                    totalApproved = response.getTotal();
                    expectedPages = (int) Math.ceil(totalApproved / (double) PAGE_SIZE);
                }
                
                int pageApproved = 0;
                if (response.getVendors() != null) {
                    for (V1Vendor vendor : response.getVendors()) {
                        approvedSellers.add(vendor.getSellerNumber());
                        pageApproved++;
                    }
                }
                if (response.getVendors() == null || response.getVendors().isEmpty()) {
                    log.warning("No vendors returned on this page; stopping pagination.");
                    break;
                }
                
                log.info(String.format("Page %d: %d vendors, %d approved cumulative", 
                    pageCount, pageApproved, approvedSellers.size()));
                
                nextPageToken = response.getNextPageToken();
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
                    log.info("More pages available, continuing...");
                }
                previousPageToken = nextPageToken;
                
            } while (nextPageToken != null && !nextPageToken.isEmpty());
            
            log.info(String.format("=== VendorRefresh END === %d pages, %d APPROVED sellers", 
                pageCount, approvedSellers.size()));
            
            // Debug: Log all approved seller numbers if small enough
            if (approvedSellers.size() <= 20) {
                log.info("Approved sellers: " + approvedSellers);
            }
            
            // Update configuration store
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("approvedSellers", new JSONArray(approvedSellers));
            ILoppisConfigurationStore.setApprovedSellers(jsonObject.toString());
            
            return true;
            
        } catch (ApiException e) {
            log.warning("Failed to refresh approved sellers: " + e.getMessage());
            return false;
        } catch (Exception e) {
            log.warning("Unexpected error refreshing approved sellers: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Check if a seller is in the approved sellers list (cached version).
     * 
     * @param sellerId the seller number to check
     * @return true if seller is approved, false otherwise
     */
    public static boolean isSellerApproved(int sellerId) {
        String approvedSellersJson = ILoppisConfigurationStore.getApprovedSellers();
        if (approvedSellersJson == null || approvedSellersJson.isBlank()) {
            return false;
        }
        
        try {
            return new JSONObject(approvedSellersJson)
                    .getJSONArray("approvedSellers")
                    .toList()
                    .contains(sellerId);
        } catch (Exception e) {
            log.warning("Failed to parse approved sellers JSON: " + e.getMessage());
            return false;
        }
    }
}
