package se.goencoder.loppiskassan.utils;

import se.goencoder.iloppis.model.V1RejectedItem;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.service.RejectedItemsManager;
import se.goencoder.loppiskassan.storage.RejectedItemEntry;
import se.goencoder.loppiskassan.storage.RejectedItemsStore;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
        if (eventId == null || eventId.isBlank() || rejectedItems == null || rejectedItems.isEmpty()) {
            return;
        }
        
        try {
            List<RejectedItemEntry> entries = new ArrayList<>();
            String timestamp = OffsetDateTime.now().format(TIMESTAMP_FORMAT);

            for (V1RejectedItem rejected : rejectedItems) {
                if (rejected == null || rejected.getItem() == null) {
                    continue;
                }

                String errorCode = rejected.getErrorCode() != null ? rejected.getErrorCode().toString() : "";
                String reason = rejected.getReason();
                if (reason == null || reason.isBlank()) {
                    reason = errorCode;
                }

                se.goencoder.loppiskassan.V1PaymentMethod paymentMethod = null;
                if (rejected.getItem().getPaymentMethod() != null) {
                    String raw = rejected.getItem().getPaymentMethod().toString();
                    if (raw != null) {
                        try {
                            paymentMethod = se.goencoder.loppiskassan.V1PaymentMethod.valueOf(raw);
                        } catch (IllegalArgumentException ignored) {
                            if ("SWISH".equalsIgnoreCase(raw)) {
                                paymentMethod = se.goencoder.loppiskassan.V1PaymentMethod.Swish;
                            } else if ("KONTANT".equalsIgnoreCase(raw) || "CASH".equalsIgnoreCase(raw)) {
                                paymentMethod = se.goencoder.loppiskassan.V1PaymentMethod.Kontant;
                            }
                        }
                    }
                }

                entries.add(new RejectedItemEntry(
                        rejected.getItem().getItemId(),
                        rejected.getItem().getPurchaseId(),
                        rejected.getItem().getSeller(),
                        rejected.getItem().getPrice(),
                        paymentMethod,
                        rejected.getItem().getSoldTime() == null
                                ? null
                                : rejected.getItem().getSoldTime().toLocalDateTime(),
                        errorCode,
                        reason,
                        timestamp
                ));
            }

            if (!entries.isEmpty()) {
                RejectedItemsStore store = new RejectedItemsStore(eventId);
                store.appendAll(entries);
                RejectedItemsManager.getInstance().notifyRejectedCountChanged(eventId);
                log.info(() -> String.format("Saved %d rejected items for event %s", entries.size(), eventId));
            }
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
