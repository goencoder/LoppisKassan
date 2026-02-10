package se.goencoder.loppiskassan.service;

import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.ui.Popup;

import java.util.List;
import java.util.logging.Logger;

/**
 * History operations for ONLINE (iLoppis) mode.
 * 
 * Supports:
 * - Payout: NOT AVAILABLE (throws UnsupportedOperationException)
 * - Archive: NOT AVAILABLE (throws UnsupportedOperationException)
 * - Sync: Upload pending + download from API
 */
public class OnlineHistoryOperations implements HistoryOperations {
    
    private static final Logger log = Logger.getLogger(OnlineHistoryOperations.class.getName());
    
    @Override
    public void performPayout(List<V1SoldItem> filteredItems) {
        log.warning("Payout attempted in online mode - operation not supported");
        throw new UnsupportedOperationException(
            LocalizationManager.tr("error.operation_not_available.payout_online"));
    }
    
    @Override
    public boolean performArchive(List<V1SoldItem> paidItems) {
        log.warning("Archive attempted in online mode - operation not supported");
        throw new UnsupportedOperationException(
            LocalizationManager.tr("error.operation_not_available.archive_online"));
    }
    
    @Override
    public void performSync(Runnable syncCallback) {
        log.info("Online sync: upload + download from API");
        syncCallback.run();
    }
    
    @Override
    public boolean isPayoutAvailable() {
        return false;
    }
    
    @Override
    public boolean isArchiveAvailable() {
        return false;
    }
}
