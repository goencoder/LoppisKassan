package se.goencoder.loppiskassan.service;

import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.loppiskassan.V1SoldItem;

import java.io.IOException;
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
    public void performPayout() throws ApiException {
        // Online mode: call API then update local
        // Implementation stays in controller for now
        throw new UnsupportedOperationException("TODO: Phase 4 implementation");
    }

    @Override
    public void handleImport() throws ApiException, IOException {
        // Online mode: sync with server
        // Implementation stays in controller for now
        throw new UnsupportedOperationException("TODO: Phase 4 implementation");
    }

    @Override
    public boolean isLocal() {
        return false;
    }
}
