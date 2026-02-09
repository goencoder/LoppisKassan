package se.goencoder.loppiskassan.service;

import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.SoldItemsServicePayoutBody;
import se.goencoder.iloppis.model.V1PaymentMethodFilter;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.ui.ProgressDialog;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Online event service implementation.
 * Operations interact with the iLoppis API and maintain local cache/fallback.
 * 
 * TODO: Full implementation in Phase 4
 */
public class OnlineEventService implements EventService {

    @Override
    public void saveSoldItems(String eventId, List<V1SoldItem> items) throws IOException, ApiException {
        // Online mode: upload to API then save locally
        // Implementation stays in controller for now
        throw new UnsupportedOperationException("TODO: Phase 4 implementation");
    }

    @Override
    public void performPayout(String eventId, String sellerFilter, String paymentMethodFilter) throws ApiException {
        // Online mode: call API to mark items as paid out
        SoldItemsServicePayoutBody payoutBody = new SoldItemsServicePayoutBody();

        if (sellerFilter != null && !sellerFilter.isEmpty()) {
            try {
                int seller = Integer.parseInt(sellerFilter);
                payoutBody.setSeller(seller);
            } catch (NumberFormatException e) {
                // Ignore invalid seller filter
            }
        }

        if (paymentMethodFilter != null && !paymentMethodFilter.isEmpty()) {
            try {
                V1PaymentMethodFilter pmFilter = V1PaymentMethodFilter.fromValue(paymentMethodFilter);
                payoutBody.setPaymentMethodFilter(pmFilter);
            } catch (IllegalArgumentException e) {
                // Ignore invalid payment method filter
            }
        }

        payoutBody.setUntilTimestamp(OffsetDateTime.now());

        // Call the API to process the payout
        ApiHelper.INSTANCE.getSoldItemsServiceApi().soldItemsServicePayout(eventId, payoutBody);
    }

    @Override
    public void synchronizeItems(SyncContext context) throws ApiException, IOException {
        // Online mode: run upload/download in a progress dialog
        if (context != null && context.getOnlineUploadDownloadOperation() != null) {
            ProgressDialog.runTask(
                context.getParentComponent(),
                context.getProgressTitle(),
                context.getProgressMessage(),
                context.getOnlineUploadDownloadOperation(),
                unused -> {},
                e -> Popup.ERROR.showAndWait(
                    LocalizationManager.tr("error.network_fetch_history.title"),
                    LocalizationManager.tr("error.network_fetch_history.message"))
            );
        }
    }

    @Override
    public boolean isLocal() {
        return false;
    }
}
