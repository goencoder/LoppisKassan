package se.goencoder.loppiskassan.localization;

import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads translations from JSON files located in {@code /lang} on the classpath.
 */
public class JsonLocalizationStrategy implements LocalizationStrategy {
    private final JSONObject translations;

    public JsonLocalizationStrategy(String languageCode) {
        this.translations = load(languageCode);
    }

    private JSONObject load(String languageCode) {
        String path = "/lang/" + languageCode + ".json";
        try (InputStream is = JsonLocalizationStrategy.class.getResourceAsStream(path)) {
            if (is == null) {
                throw new IllegalArgumentException("Missing language file: " + path);
            }
            String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return new JSONObject(text);
        } catch (IOException e) {
            throw new RuntimeException("Could not load language file " + path, e);
        }
    }

    @Override
    public String get(String key) {
        return translations.optString(key, key);
    }
}
