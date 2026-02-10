package se.goencoder.loppiskassan.utils;

import org.json.JSONObject;
import se.goencoder.iloppis.model.V1RejectedItem;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.storage.LocalEventPaths;

import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.logging.Logger;

/**
 * Helper for handling rejected items from API responses.
 * Saves rejected items to local log and builds user-facing messages.
 */
public class RejectedItemsHelper {
    
    private static final Logger log = Logger.getLogger(RejectedItemsHelper.class.getName());
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Save rejected items to the rejection log file.
     * 
     * @param eventId the event ID
     * @param rejectedItems list of rejected items from API response
     */
    public static void saveRejectedItems(String eventId, List<V1RejectedItem> rejectedItems) {
        if (rejectedItems == null || rejectedItems.isEmpty()) {
            return;
        }
        
        try {
            Path rejectedPath = LocalEventPaths.getRejectedPurchasesPath(eventId);
            if (rejectedPath.getParent() != null) {
                Files.createDirectories(rejectedPath.getParent());
            }
            
            try (BufferedWriter writer = Files.newBufferedWriter(
                    rejectedPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND)) {
                
                for (V1RejectedItem rejected : rejectedItems) {
                    JSONObject entry = new JSONObject();
                    entry.put("timestamp", OffsetDateTime.now().format(TIMESTAMP_FORMAT));
                    entry.put("errorCode", rejected.getErrorCode() != null ? rejected.getErrorCode().toString() : "UNKNOWN");
                    entry.put("reason", rejected.getReason() != null ? rejected.getReason() : "No reason");
                    
                    // Add item details if available
                    if (rejected.getItem() != null) {
                        JSONObject itemDetails = new JSONObject();
                        itemDetails.put("itemId", rejected.getItem().getItemId());
                        itemDetails.put("seller", rejected.getItem().getSeller());
                        itemDetails.put("price", rejected.getItem().getPrice());
                        itemDetails.put("paymentMethod", rejected.getItem().getPaymentMethod());
                        entry.put("item", itemDetails);
                    }
                    
                    writer.write(entry.toString());
                    writer.newLine();
                }
            }
            
            log.info(() -> String.format("Saved %d rejected items to %s", rejectedItems.size(), rejectedPath));
            
        } catch (Exception e) {
            log.warning("Failed to save rejected items: " + e.getMessage());
        }
    }
    
    /**
     * Build a user-facing message describing rejected items.
     * 
     * @param rejectedItems list of rejected items
     * @return formatted message for popup display
     */
    public static String buildRejectionMessage(List<V1RejectedItem> rejectedItems) {
        if (rejectedItems == null || rejectedItems.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(LocalizationManager.tr("error.upload_rejected.header"));
        
        for (V1RejectedItem rejected : rejectedItems) {
            String timestamp = OffsetDateTime.now().format(TIMESTAMP_FORMAT);
            String seller = "?";
            String price = "?";
            String payment = "?";
            String reason = rejected.getReason() != null ? rejected.getReason() : 
                           (rejected.getErrorCode() != null ? rejected.getErrorCode().toString() : "Unknown");
            
            if (rejected.getItem() != null) {
                if (rejected.getItem().getSeller() != null) {
                    seller = String.valueOf(rejected.getItem().getSeller());
                }
                if (rejected.getItem().getPrice() != null) {
                    price = String.format("%.2f SEK", rejected.getItem().getPrice() / 1.0);
                }
                if (rejected.getItem().getPaymentMethod() != null) {
                    payment = rejected.getItem().getPaymentMethod().toString();
                }
            }
            
            String entry = LocalizationManager.tr("error.upload_rejected.entry", 
                timestamp, seller, price, payment, reason);
            sb.append(entry);
        }
        
        return sb.toString();
    }
}
