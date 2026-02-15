package se.goencoder.loppiskassan.service;

import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.config.ILoppisConfigurationStore;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.utils.VendorRefreshHelper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

/**
 * Cashier strategy for iLOPPIS mode (online market with central server).
 *
 * <p><b>Local-first:</b> Items are written to disk first, and uploads happen
 * on the {@link BackgroundSyncManager} thread. This keeps the cashier UI fast
 * and ensures all file I/O is serialized on a single background thread.</p>
 *
 * <p>Seller validation is still done against the cached approved sellers list.</p>
 */
public class IloppisCashierStrategy implements CashierStrategy {

    private static final Logger log = Logger.getLogger(IloppisCashierStrategy.class.getName());

    @Override
    public boolean validateSeller(int sellerId) {
        // First check: Use cached approved sellers list
        log.info(String.format("=== validateSeller(%d) START ===", sellerId));

        if (VendorRefreshHelper.isSellerApproved(sellerId)) {
            log.info(String.format("✓ Seller %d found in cache (approved)", sellerId));
            return true;
        }

        // Seller not found in cache - attempt auto-recovery by refreshing list
        log.info(String.format("✗ Seller %d NOT in cached list - attempting auto-recovery", sellerId));

        String eventId = ILoppisConfigurationStore.getEventId();
        boolean refreshSuccess = VendorRefreshHelper.refreshApprovedSellers(eventId);
        log.info(String.format("Auto-recovery refresh result: %s", refreshSuccess ? "SUCCESS" : "FAILED"));

        if (refreshSuccess) {
            boolean nowApproved = VendorRefreshHelper.isSellerApproved(sellerId);
            if (nowApproved) {
                log.info(String.format("✓ Auto-recovery SUCCESS: Seller %d now approved", sellerId));
                return true;
            } else {
                log.warning(String.format("✗ Auto-recovery FAILED: Seller %d still not in approved list", sellerId));
            }
        }

        log.warning(String.format("=== validateSeller(%d) END === RESULT: NOT APPROVED", sellerId));
        return false;
    }

    @Override
    public String getSellerValidationErrorKey() {
        return "cashier.seller_not_approved";
    }

    @Override
    public boolean persistItems(List<V1SoldItem> items, String purchaseId, V1PaymentMethod paymentMethod,
                                LocalDateTime soldTime) throws Exception {
        String eventId = ILoppisConfigurationStore.getEventId();
        if (eventId == null || eventId.isBlank()) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("error.no_event_selected.title"),
                    LocalizationManager.tr("error.no_event_selected.message"));
            return false;
        }

        // Local-first: enqueue for background persistence + upload.
        BackgroundSyncManager.getInstance().enqueueItems(eventId, items);
        return true;
    }

    @Override
    public String getModeDescription() {
        return "iloppis";
    }
}
