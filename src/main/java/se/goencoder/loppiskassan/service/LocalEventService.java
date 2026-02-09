package se.goencoder.loppiskassan.service;

import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.utils.FileUtils;

import java.io.IOException;
import java.util.List;

/**
 * Local-only event service implementation.
 * All operations work with local files, no network calls.
 * 
 * TODO: Full implementation in Phase 4
 */
public class LocalEventService implements EventService {

    @Override
    public void saveSoldItems(String eventId, List<V1SoldItem> items) throws IOException {
        // Mark all items as not uploaded (local mode)
        items.forEach(item -> item.setUploaded(false));
        
        // Save to local file
        FileUtils.appendSoldItems(eventId, items);
    }

    @Override
    public void performPayout() throws ApiException {
        // Local mode: no API call needed
        // Payout handling stays in controller for now
    }

    @Override
    public void handleImport() throws ApiException, IOException {
        // Local mode: file import
        // Import handling stays in controller for now
    }

    @Override
    public boolean isLocal() {
        return true;
    }
}
