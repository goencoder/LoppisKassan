package se.goencoder.loppiskassan.ui;

/**
 * Contract for tabs that want a callback when they become active/selected.
 */
public interface SelectabableTab {

    /**
     * Called by the tab container when this tab becomes the active selection.
     * Implementations typically refresh data, focus a default input, or update UI state.
     */
    void selected();
}
