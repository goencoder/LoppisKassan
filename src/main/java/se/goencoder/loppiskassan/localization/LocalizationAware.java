package se.goencoder.loppiskassan.localization;

/**
 * Components that need to update their displayed texts when the language
 * changes should implement this interface.
 */
public interface LocalizationAware {
    /** Reload all user-facing texts from the {@link LocalizationManager}. */
    void reloadTexts();
}

