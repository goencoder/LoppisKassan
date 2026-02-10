package se.goencoder.loppiskassan.controller;

import se.goencoder.iloppis.model.SoldItemsServiceCreateSoldItemsBody;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.V1CreateSoldItemsResponse;
import se.goencoder.iloppis.model.V1Event;
import se.goencoder.iloppis.model.V1GetApiKeyResponse;
import se.goencoder.iloppis.model.V1RejectedItem;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.model.BulkUploadResult;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.storage.LocalEvent;
import se.goencoder.loppiskassan.storage.LocalEventRepository;
import se.goencoder.loppiskassan.storage.PendingItemsStore;
import se.goencoder.loppiskassan.utils.SoldItemUtils;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for bulk-uploading local event data to iLoppis backend.
 * Handles grouping by purchaseId, sequential upload, and result aggregation.
 */
public class BulkUploadController {

    private static final long RATE_LIMIT_DELAY_MS = 100;
    private static final int MAX_RETRIES = 3;

    /**
     * Upload all pending items for a local event to the backend.
     * 
     * @param localEvent the local event to upload from
     * @param backendEvent the backend event to upload to
     * @param code the cashier code for authentication
     * @return the upload result with accepted/failed/duplicate items
     */
    public static BulkUploadResult uploadLocalEventData(
        LocalEvent localEvent,
        V1Event backendEvent,
        String code
    ) {
        BulkUploadResult result = new BulkUploadResult();

        try {
            // 1. Exchange code for API key
            String apiKey = exchangeCodeForApiKey(backendEvent.getId(), code);
            ApiHelper.INSTANCE.setCurrentApiKey(apiKey);

            // 2. Read local items (only those not already uploaded)
            List<V1SoldItem> items = readLocalItems(localEvent.getEventId());
            
            if (items.isEmpty()) {
                result.addError("No pending items to upload");
                return result;
            }

            // 3. Group by purchaseId
            Map<String, List<V1SoldItem>> grouped = items.stream()
                .collect(Collectors.groupingBy(V1SoldItem::getPurchaseId));

            // 4. Upload each group sequentially
            for (Map.Entry<String, List<V1SoldItem>> entry : grouped.entrySet()) {
                uploadPurchaseGroup(
                    backendEvent.getId(),
                    entry.getValue(),
                    result
                );
                
                // Rate limiting between groups
                Thread.sleep(RATE_LIMIT_DELAY_MS);
            }

            // 5. Update local metadata if upload was successful
            if (!result.acceptedItems.isEmpty() || !result.duplicateItems.isEmpty()) {
                updateLocalMetadata(localEvent, backendEvent, result);
            }

        } catch (ApiException e) {
            handleApiException(e, result);
        } catch (IOException e) {
            result.addError("IO error: " + e.getMessage());
        } catch (InterruptedException e) {
            result.addError("Upload interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        }

        return result;
    }

    /**
     * Exchange code for API key via backend
     */
    private static String exchangeCodeForApiKey(String eventId, String code) throws ApiException {
        V1GetApiKeyResponse response = ApiHelper.INSTANCE
            .getApiKeyServiceApi()
            .apiKeyServiceGetApiKey(eventId, code, null);
        
        if (response == null || response.getApiKey() == null || response.getApiKey().isEmpty()) {
            throw new ApiException("Failed to obtain API key from code");
        }
        
        return response.getApiKey();
    }

    /**
     * Read pending items (not yet uploaded) for a local event
     */
    private static List<V1SoldItem> readLocalItems(String eventId) throws IOException {
        PendingItemsStore store = new PendingItemsStore(eventId);
        return store.readPending();
    }

    /**
     * Upload a single purchase group with retry logic
     */
    private static void uploadPurchaseGroup(
        String eventId,
        List<V1SoldItem> items,
        BulkUploadResult result
    ) {
        for (int retry = 0; retry < MAX_RETRIES; retry++) {
            try {
                uploadGroupAttempt(eventId, items, result);
                return; // Success
            } catch (ApiException e) {
                if (retry < MAX_RETRIES - 1) {
                    // Retry with exponential backoff
                    long delay = (long) (Math.pow(2, retry) * 100);
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                } else {
                    // Final attempt failed
                    handleGroupUploadFailure(e, items, result);
                }
            }
        }
    }

    /**
     * Perform a single upload attempt for a purchase group
     */
    private static void uploadGroupAttempt(
        String eventId,
        List<V1SoldItem> items,
        BulkUploadResult result
    ) throws ApiException {
        SoldItemsServiceCreateSoldItemsBody requestBody = new SoldItemsServiceCreateSoldItemsBody();

        for (V1SoldItem localItem : items) {
            se.goencoder.iloppis.model.V1SoldItem apiItem = SoldItemUtils.toApiSoldItem(localItem);
            apiItem.setSoldTime(OffsetDateTime.of(
                    localItem.getSoldTime(),
                    OffsetDateTime.now().getOffset()
            ));
            requestBody.addItemsItem(apiItem);
        }

        V1CreateSoldItemsResponse response = ApiHelper.INSTANCE
            .getSoldItemsServiceApi()
            .soldItemsServiceCreateSoldItems(eventId, requestBody);

        if (response != null) {
            // Add accepted items
            if (response.getAcceptedItems() != null) {
                List<V1SoldItem> acceptedLocal = new ArrayList<>();
                for (se.goencoder.iloppis.model.V1SoldItem apiItem : response.getAcceptedItems()) {
                    V1SoldItem localItem = SoldItemUtils.fromApiSoldItem(apiItem, true);
                    acceptedLocal.add(localItem);
                }
                result.acceptedItems.addAll(acceptedLocal);
            }

            // Separate rejected items: duplicates vs errors
            if (response.getRejectedItems() != null) {
                for (V1RejectedItem rejected : response.getRejectedItems()) {
                    if (rejected.getErrorCode() != null && 
                        "DUPLICATE_RECEIPT".equals(rejected.getErrorCode().toString())) {
                        result.duplicateItems.add(rejected);
                    } else {
                        result.failedItems.add(rejected);
                    }
                }
            }
        }
    }

    /**
     * Handle upload failure for a purchase group
     */
    private static void handleGroupUploadFailure(
        ApiException e,
        List<V1SoldItem> items,
        BulkUploadResult result
    ) {
        result.addError(String.format(
            "Failed to upload purchase group: %s (HTTP %d)",
            e.getMessage(),
            e.getCode()
        ));
    }

    /**
     * Handle API exception with specific error handling
     */
    private static void handleApiException(ApiException e, BulkUploadResult result) {
        String message;
        
        if (e.getCode() == 401) {
            message = "Invalid code or unauthorized";
        } else if (e.getCode() == 403) {
            message = "Permission denied for this event";
        } else if (e.getCode() == 404) {
            message = "Event not found on backend";
        } else if (e.getCode() == 0) {
            message = "Network error - cannot reach server";
        } else {
            message = String.format("API error (HTTP %d): %s", e.getCode(), e.getMessage());
        }
        
        result.addError(message);
    }

    /**
     * Update local event metadata after successful upload
     */
    private static void updateLocalMetadata(LocalEvent localEvent, V1Event backendEvent, BulkUploadResult result) 
        throws IOException {
        // Read ALL items (including already-uploaded ones)
        PendingItemsStore store = new PendingItemsStore(localEvent.getEventId());
        List<V1SoldItem> allItems = store.readAll();
        
        // Mark uploaded items in the full list
        for (V1SoldItem localItem : allItems) {
            // Check if this item was in accepted list (by itemId)
            boolean wasAccepted = result.acceptedItems.stream()
                .anyMatch(apiItem -> apiItem.getItemId() != null && 
                         apiItem.getItemId().equals(localItem.getItemId()));
            
            // Check if this item was in duplicate list (by itemId)
            boolean wasDuplicate = result.duplicateItems.stream()
                .anyMatch(rejected -> rejected.getItem() != null && 
                         rejected.getItem().getItemId() != null &&
                         rejected.getItem().getItemId().equals(localItem.getItemId()));
            
            if (wasAccepted || wasDuplicate) {
                localItem.setUploaded(true);
            }
        }
        
        // Save updated items back to store
        store.saveAll(allItems);
        
        // Create updated event with upload metadata
        LocalEvent updated = new LocalEvent(
            localEvent.getEventId(),
            localEvent.getEventType(),
            localEvent.getName(),
            localEvent.getDescription(),
            localEvent.getAddressStreet(),
            localEvent.getAddressCity(),
            localEvent.getCreatedAt(),
            localEvent.getRevenueSplit()
        );
        
        // Save updated event
        LocalEventRepository.save(updated);
    }
}
