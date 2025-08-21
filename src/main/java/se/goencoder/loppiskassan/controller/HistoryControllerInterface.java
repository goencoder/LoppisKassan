package se.goencoder.loppiskassan.controller;

import se.goencoder.loppiskassan.ui.HistoryPanelInterface;

/**
 * Controller contract for the history screen.
 * <p>
 * Mode behavior:
 * <ul>
 *   <li><b>Online:</b> may load/refresh from the backend and support server-side filtering.</li>
 *   <li><b>Offline:</b> loads from local session data or cached storage only.</li>
 * </ul>
 */
public interface HistoryControllerInterface {

    /**
     * Register the history view so the controller can push data and read filters.
     * @param view history panel view
     */
    void registerView(HistoryPanelInterface view);

    /**
     * Load or refresh the current history data set, obeying active filters.
     * Online mode may call the backend; offline mode should read local data.
     */
    void loadHistory();

    /**
     * Notify the controller that one or more filters changed and data should be re-evaluated.
     * Implementations should debounce as needed.
     */
    void filterUpdated();

    /**
     * Handle a button action exposed by the history view (e.g., "import", "export").
     * @param actionCommand logical action command string from the view
     */
    void buttonAction(String actionCommand);
}
