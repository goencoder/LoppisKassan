package se.goencoder.loppiskassan.service;

import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.ui.Popup;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * History operations for LOCAL mode.
 * 
 * Supports:
 * - Payout: Mark items as collected (file update)
 * - Archive: Move paid items to archive file
 * - Sync: Import CSV data
 */
public class LocalHistoryOperations implements HistoryOperations {
    
    private static final Logger log = Logger.getLogger(LocalHistoryOperations.class.getName());
    
    private final LocalHistoryCallback callback;
    
    public LocalHistoryOperations(LocalHistoryCallback callback) {
        this.callback = callback;
    }
    
    @Override
    public void performPayout(List<V1SoldItem> filteredItems) {
        log.info(() -> String.format("Local payout: marking %d items as collected", filteredItems.size()));
        
        LocalDateTime now = LocalDateTime.now();
        filteredItems.forEach(item -> item.setCollectedBySellerTime(now));
        
        callback.saveHistoryToFile();
        callback.refreshView();
    }
    
    @Override
    public boolean performArchive(List<V1SoldItem> paidItems) {
        log.info(() -> String.format("Local archive: archiving %d paid items", paidItems.size()));
        
        try {
            callback.archiveItemsToFile(paidItems);
            callback.removeItems(paidItems);
            callback.saveHistoryToFile();
            callback.updateDistinctSellers();
            callback.refreshView();
            return true;
        } catch (Exception e) {
            log.severe("Archive failed: " + e.getMessage());
            Popup.ERROR.showAndWait(
                LocalizationManager.tr("error.archive.title"),
                LocalizationManager.tr("error.archive.message", e.getMessage()));
            return false;
        }
    }
    
    @Override
    public void performSync(Runnable syncCallback) {
        log.info("Local sync: importing CSV data");
        syncCallback.run();
    }
    
    @Override
    public boolean isPayoutAvailable() {
        return true;
    }
    
    @Override
    public boolean isArchiveAvailable() {
        return true;
    }
    
    /**
     * Callback interface for LocalHistoryOperations to interact with controller.
     */
    public interface LocalHistoryCallback {
        void saveHistoryToFile();
        void archiveItemsToFile(List<V1SoldItem> items);
        void removeItems(List<V1SoldItem> items);
        void updateDistinctSellers();
        void refreshView();
    }
}
