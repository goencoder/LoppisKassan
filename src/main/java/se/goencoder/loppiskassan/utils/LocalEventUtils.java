package se.goencoder.loppiskassan.utils;

import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.localization.LocalizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

/**
 * Utility class for working with local event data.
 */
public final class LocalEventUtils {
    
    private LocalEventUtils() {}
    
    /**
     * Count the number of sales for a local event.
     * 
     * @param eventId the event ID to count sales for
     * @return number of sales (items), or 0 if no sales or error
     */
    public static int getSalesCount(String eventId) {
        try {
            if (eventId == null || !eventId.startsWith("local-")) {
                return 0;
            }
            
            var pendingPath = LocalEventPaths.getPendingItemsPath(eventId);
            if (Files.notExists(pendingPath)) {
                return 0;
            }
            
            List<V1SoldItem> items = JsonlHelper.readItems(pendingPath);
            return items.size();
            
        } catch (IOException e) {
            // Log error but don't fail - just return 0
            System.err.println("Failed to count sales for event " + eventId + ": " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Get the status of a local event based on its sales count.
     * 
     * @param eventId the event ID
     * @param isClosed whether the event is manually closed
     * @return status icon as String
     */
    public static String getLocalEventStatus(String eventId, boolean isClosed) {
        if (isClosed) {
            return "🔒"; // Closed
        }
        
        int salesCount = getSalesCount(eventId);
        if (salesCount == 0) {
            return "📭"; // Empty/No sales
        } else {
            return "🟢"; // Active/Has sales
        }
    }
    
    /**
     * Get a formatted status text for local events.
     * 
     * @param eventId the event ID
     * @param isClosed whether the event is manually closed
     * @return formatted string like "🟢 Active (5 sales)" or "📭 Empty"
     */
    public static String getLocalEventStatusText(String eventId, boolean isClosed) {
        int salesCount = getSalesCount(eventId);
        
        if (isClosed) {
            return LocalizationManager.tr("export.local_event.closed", String.valueOf(salesCount));
        } else if (salesCount == 0) {
            return LocalizationManager.tr("export.local_event.empty");
        } else {
            return LocalizationManager.tr("export.local_event.active", String.valueOf(salesCount));
        }
    }
}