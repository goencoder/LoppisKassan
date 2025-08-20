package se.goencoder.loppiskassan.localization;

import se.goencoder.loppiskassan.config.ConfigurationStore;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * Singleton manager for localization. Uses a {@link LocalizationStrategy}
 * to retrieve translations. The default language is Swedish.
 */
public final class LocalizationManager {
    private static LocalizationStrategy strategy;

    /** Listener for language change events. */
    public interface LanguageChangeListener { void onLanguageChanged(); }

    private static final List<LanguageChangeListener> listeners = new ArrayList<>();

    private LocalizationManager() {}

    public static void addListener(LanguageChangeListener l) { listeners.add(l); }

    public static void removeListener(LanguageChangeListener l) { listeners.remove(l); }

    /**
     * Initialize the localization system. Should be called once at application startup.
     */
    public static void initialize() {
        // Ensure language is set to default if not already configured
        if (ConfigurationStore.LANGUAGE_STR.get() == null) {
            ConfigurationStore.LANGUAGE_STR.set("sv");
        }
        // Initialize strategy with the configured language
        strategy = new JsonLocalizationStrategy(ConfigurationStore.LANGUAGE_STR.get());
    }

    /**
     * Changes the current language and notifies listeners.
     *
     * @param languageCode ISO language code (e.g. "sv", "en")
     */
    public static void setLanguage(String languageCode) {
        ConfigurationStore.LANGUAGE_STR.set(languageCode);
        strategy = new JsonLocalizationStrategy(languageCode);
        for (LanguageChangeListener l : new ArrayList<>(listeners)) {
            l.onLanguageChanged();
        }
    }

    public static String getLanguage() {
        return ConfigurationStore.LANGUAGE_STR.get();
    }

    public static void reloadFromConfig() {
        strategy = new JsonLocalizationStrategy(ConfigurationStore.LANGUAGE_STR.get());
    }

    /**
     * Returns the translation for the given key, formatting using
     * {@link MessageFormat} when arguments are provided.
     */
    public static String tr(String key, Object... args) {
        // Ensure strategy is initialized
        if (strategy == null) {
            initialize();
        }
        String value = strategy.get(key);
        return args.length > 0 ? MessageFormat.format(value, args) : value;
    }
}
