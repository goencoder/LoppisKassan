package se.goencoder.loppiskassan.service;

import se.goencoder.loppiskassan.storage.RejectedItemsStore;

import javax.swing.SwingUtilities;

/**
 * Tracks rejected item count for UI updates.
 */
public class RejectedItemsManager {
    public interface RejectedCountListener {
        void onRejectedCountChanged(int rejectedCount);
    }

    private static RejectedItemsManager instance;
    private RejectedCountListener rejectedCountListener;

    private RejectedItemsManager() {
    }

    public static synchronized RejectedItemsManager getInstance() {
        if (instance == null) {
            instance = new RejectedItemsManager();
        }
        return instance;
    }

    public void setRejectedCountListener(RejectedCountListener listener) {
        this.rejectedCountListener = listener;
    }

    public int getRejectedCount(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return 0;
        }
        return new RejectedItemsStore(eventId).count();
    }

    public void notifyRejectedCountChanged(String eventId) {
        if (rejectedCountListener == null) {
            return;
        }
        int count = getRejectedCount(eventId);
        SwingUtilities.invokeLater(() -> rejectedCountListener.onRejectedCountChanged(count));
    }
}
