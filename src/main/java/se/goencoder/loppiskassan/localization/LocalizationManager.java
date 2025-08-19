package se.goencoder.loppiskassan.localization;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Singleton manager for localization. Uses a {@link LocalizationStrategy}
 * to retrieve translations. The default language is Swedish.
 */
public final class LocalizationManager {
    private static LocalizationStrategy strategy = new JsonLocalizationStrategy("sv");

    /** Listener for language change events. */
    public interface LanguageChangeListener { void onLanguageChanged(); }

    private static final List<LanguageChangeListener> listeners = new ArrayList<>();

    private LocalizationManager() {}

    public static void addListener(LanguageChangeListener l) { listeners.add(l); }

    public static void removeListener(LanguageChangeListener l) { listeners.remove(l); }

    /**
     * Changes the current language and notifies listeners.
     *
     * @param languageCode ISO language code (e.g. "sv", "en")
     */
    public static void setLanguage(String languageCode) {
        strategy = new JsonLocalizationStrategy(languageCode);
        for (LanguageChangeListener l : new ArrayList<>(listeners)) {
            l.onLanguageChanged();
        }
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
