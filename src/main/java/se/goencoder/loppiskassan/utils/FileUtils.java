package se.goencoder.loppiskassan.utils;

import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.storage.LocalEventRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class FileUtils {
    /**
     * Save sold items to file.
     * Note! This method will truncate and then save the items to the JSONL file.
     */
    public static void saveSoldItems(String eventId, List<V1SoldItem> items) throws IOException {
        LocalEventRepository.ensureEventStorage(eventId);
        Path path = LocalEventPaths.getPendingItemsPath(eventId);
        JsonlHelper.writeItems(path, items);
    }

    /**
     * Append sold items to file.
     * Note! This method will append the items to the JSONL file without creating a backup.
     */
    public static void appendSoldItems(String eventId, List<V1SoldItem> items) throws IOException {
        LocalEventRepository.ensureEventStorage(eventId);
        Path path = LocalEventPaths.getPendingItemsPath(eventId);
        JsonlHelper.appendItems(path, items);
    }


}
