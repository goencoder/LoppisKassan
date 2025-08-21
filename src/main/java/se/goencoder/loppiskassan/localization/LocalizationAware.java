package se.goencoder.loppiskassan.localization;

/**
 * Marker for views/controllers that need to react when the UI language changes.
 */
public interface LocalizationAware {

    /**
     * Reload all user-visible texts from the active {@link se.goencoder.loppiskassan.localization.LocalizationManager}.
     * Implementations should update labels, tooltips, table headers, and any cached texts.
     */
    void reloadTexts();
}

