package se.goencoder.loppiskassan.localization;

/**
 * Lookup strategy for localized strings.
 */
public interface LocalizationStrategy {

    /**
     * Return a localized message for the given i18n key.
     * <p>
     * Implementations should fall back to the key itself if the translation is missing,
     * and may support parameter interpolation depending on the backing store.
     *
     * @param key i18n message key (e.g., "cashier.open")
     * @return localized string, or the key if not found
     */
    String get(String key);
}
