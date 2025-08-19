package se.goencoder.loppiskassan.localization;

import java.text.MessageFormat;

/**
 * Singleton manager for localization. Uses a {@link LocalizationStrategy}
 * to retrieve translations. The default language is Swedish.
 */
public final class LocalizationManager {
    private static LocalizationStrategy strategy = new JsonLocalizationStrategy("sv");

    private LocalizationManager() {}

    /**
     * Changes the current language.
     *
     * @param languageCode ISO language code (e.g. "sv", "en")
     */
    public static void setLanguage(String languageCode) {
        strategy = new JsonLocalizationStrategy(languageCode);
    }

    /**
     * Returns the translation for the given key, formatting using
     * {@link MessageFormat} when arguments are provided.
     */
    public static String tr(String key, Object... args) {
        String value = strategy.get(key);
        return args.length > 0 ? MessageFormat.format(value, args) : value;
    }
}
