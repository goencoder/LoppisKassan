package se.goencoder.loppiskassan.localization;

/**
 * Strategy interface for providing translations.
 */
public interface LocalizationStrategy {
    /**
     * Returns the translation for the given key.
     *
     * @param key translation key
     * @return translated string or the key itself if missing
     */
    String get(String key);
}
