package se.goencoder.loppiskassan.controller;


import org.json.JSONArray;
import org.json.JSONObject;
import se.goencoder.iloppis.api.ApiKeyServiceApi;
import se.goencoder.iloppis.api.EventServiceApi;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.iloppis.model.*;
import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.rest.ApiHelper;
import se.goencoder.loppiskassan.ui.DiscoveryPanelInterface;
import se.goencoder.loppiskassan.ui.Popup;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DiscoveryTabController implements DiscoveryControllerInterface {

    private static final DiscoveryTabController instance = new DiscoveryTabController();
    private DiscoveryPanelInterface view;

    private DiscoveryTabController() {}

    public static DiscoveryTabController getInstance() {
        return instance;
    }

    @Override
    public void registerView(DiscoveryPanelInterface view) {
        this.view = view;
    }

    @Override
    public void discoverEvents(String dateFrom) {
        EventFilter filter = new EventFilter().dateFrom(dateFrom);
        Pagination pagination = new Pagination().pageSize(100);

        FilterEventsRequest request = new FilterEventsRequest()
                .filter(filter)
                .pagination(pagination);

        // Clear current table
        view.clearEventsTable();

        try {
            // Call out to EventServiceApi
            EventServiceApi eventApi = ApiHelper.INSTANCE.getEventServiceApi();
            FilterEventsResponse response = eventApi.eventServiceFilterEvents(request);

            List<Event> discovered = response.getEvents();
            // Update UI
            view.populateEventsTable(discovered);
            view.showStatusMessage("Hittade " + discovered.size() + " event.");

        } catch (Exception ex) {
            if (ex instanceof ApiException) {
                Popup.ERROR.showAndWait("Kunde inte hämta event", ex);
            } else {
                Popup.ERROR.showAndWait("Ett fel uppstod", ex);
            }
        }
    }

    @Override
    public void fetchApiKey(String eventId, String cashierCode) {
        if (eventId == null || eventId.isEmpty()) {
            Popup.WARNING.showAndWait("Ingen rad vald", "Du måste välja ett event först.");
            return;
        }
        if (cashierCode == null || cashierCode.isEmpty()) {
            Popup.WARNING.showAndWait("Ingen kod", "Ange en kassakod.");
            return;
        }

        try {
            ApiKeyServiceApi apiKeyServiceApi = ApiHelper.INSTANCE.getApiKeyServiceApi();
            GetApiKeyResponse response = apiKeyServiceApi.apiKeyServiceGetApiKey(eventId, cashierCode);

            // If success, store key in ApiHelper
            ApiHelper.INSTANCE.setCurrentApiKey(response.getApiKey());
            ConfigurationStore.EVENT_ID_STR.set(eventId);
            ConfigurationStore.API_KEY_STR.set(response.getApiKey());
            ListVendorApplicationsResponse res = ApiHelper.INSTANCE.getVendorApplicationServiceApi().vendorApplicationServiceListVendorApplications(
                    ConfigurationStore.EVENT_ID_STR.get(),
                    500,
                    ""
            );
            // Collect approved sellers
            Set<Integer> approvedSellers = new HashSet<>();
            List<VendorApplication> applications = res.getApplications();
            assert applications != null;
            for (VendorApplication application : applications) {
                if ("APPROVED".equalsIgnoreCase(application.getStatus())) {
                    approvedSellers.add(application.getSellerNumber());
                }
            }

            // Convert to JSON
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("approvedSellers", new JSONArray(approvedSellers));

            // Store in ConfigurationStore
            ConfigurationStore.APPROVED_SELLERS_JSON.set(jsonObject.toString());

            view.showStatusMessage("Fick token: " + response.getApiKey());
            Popup.INFORMATION.showAndWait("Ok!", "Kassan är redo att användas.");
            // Switch to the next tab


        } catch (Exception ex) {
            if (ex instanceof ApiException) {
                Popup.ERROR.showAndWait("Kunde inte hämta token", ex);
            } else {
                Popup.ERROR.showAndWait("Ett fel uppstod", ex);
            }
        }
    }

    @Override
    public Set<Integer> getApprovedSellersForEvent(String eventId) {
        // For now, a dummy example. Eventually you might do:
        // 1) Call some endpoint to get a list of approved sellers for this event
        // 2) Return them
        // We'll just return an empty set or a mock set for now.
        return new HashSet<>(Collections.singletonList(1));
        // e.g. just say "Seller #1 is approved" as a placeholder
    }
}
