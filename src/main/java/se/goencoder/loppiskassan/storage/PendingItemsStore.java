package se.goencoder.loppiskassan.storage;

import se.goencoder.loppiskassan.V1SoldItem;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class PendingItemsStore {
    private final String eventId;

    public PendingItemsStore(String eventId) {
        this.eventId = eventId;
    }

    public void appendItems(List<V1SoldItem> items) throws IOException {
        Path path = LocalEventPaths.getPendingItemsPath(eventId);
        JsonlHelper.appendItems(path, items);
    }

    public List<V1SoldItem> readAll() throws IOException {
        Path path = LocalEventPaths.getPendingItemsPath(eventId);
        return JsonlHelper.readItems(path);
    }

    /**
     * Read only items that have not been uploaded yet
     */
    public List<V1SoldItem> readPending() throws IOException {
        List<V1SoldItem> all = readAll();
        return all.stream()
            .filter(item -> !item.isUploaded())
            .toList();
    }

    /**
     * Save all items back to the store (overwrites existing file)
     */
    public void saveAll(List<V1SoldItem> items) throws IOException {
        Path path = LocalEventPaths.getPendingItemsPath(eventId);
        JsonlHelper.writeItems(path, items);
    }
}
