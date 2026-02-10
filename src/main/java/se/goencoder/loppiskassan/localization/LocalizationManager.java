package se.goencoder.loppiskassan.localization;

import se.goencoder.loppiskassan.config.GlobalConfigurationStore;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton manager for localization. Uses a {@link LocalizationStrategy}
 * to retrieve translations. The default language is Swedish.
 */
public final class LocalizationManager {
    /** Listener for language change events. */
    public interface LanguageChangeListener { void onLanguageChanged(); }

    private static final List<LanguageChangeListener> listeners = new ArrayList<>();
    private static final ConcurrentHashMap<String, LocalizationStrategy> cache = new ConcurrentHashMap<>();
    private static volatile LocalizationStrategy currentStrategy;

    private LocalizationManager() {}

    public static void addListener(LanguageChangeListener l) { listeners.add(l); }

    public static void removeListener(LanguageChangeListener l) { listeners.remove(l); }

    /**
     * Initialize the localization system. Should be called once at application startup.
     * Always defaults to Swedish (sv) as the primary language.
     */
    public static void initialize() {
        // Always start with Swedish as the default language
        GlobalConfigurationStore.setLanguage("sv");
        currentStrategy = getOrCreateStrategy("sv");
    }

    /**
     * Changes the current language and notifies listeners.
     *
     * @param languageCode ISO language code (e.g. "sv", "en")
     */
    public static void setLanguage(String languageCode) {
        GlobalConfigurationStore.setLanguage(languageCode);
        currentStrategy = getOrCreateStrategy(languageCode);
        for (LanguageChangeListener l : new ArrayList<>(listeners)) {
            l.onLanguageChanged();
        }
    }

    public static String getLanguage() {
        return GlobalConfigurationStore.getLanguage();
    }

    /**
     * Returns the translation for the given key, formatting using
     * {@link MessageFormat} when arguments are provided.
     */
    public static String tr(String key, Object... args) {
        LocalizationStrategy strategy = currentStrategy;
        if (strategy == null) {
            strategy = getOrCreateStrategy(getLanguage());
            currentStrategy = strategy;
        }
        String value = strategy.get(key);
        return args.length > 0 ? MessageFormat.format(value, args) : value;
    }

    private static LocalizationStrategy getOrCreateStrategy(String languageCode) {
        return cache.computeIfAbsent(languageCode, JsonLocalizationStrategy::new);
    }
}
