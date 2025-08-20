package se.goencoder.loppiskassan.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.localization.LocalizationManager;

import static org.junit.jupiter.api.Assertions.*;

class LanguageSelectorTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    @BeforeEach
    void resetConfig() {
        ConfigurationStore.reset();
        LocalizationManager.reloadFromConfig();
    }

    @Test
    void selectingLanguagePersistsInConfig() {
        LanguageSelector selector = new LanguageSelector();
        assertEquals("sv", selector.getSelectedLanguageCode());

        selector.selectLanguage("en");
        assertEquals("en", selector.getSelectedLanguageCode());
        assertEquals("en", ConfigurationStore.LANGUAGE_STR.get());
    }

    @Test
    void usesPersistedLanguageOnInit() {
        ConfigurationStore.LANGUAGE_STR.set("en");
        LocalizationManager.reloadFromConfig();

        LanguageSelector selector = new LanguageSelector();
        assertEquals("en", selector.getSelectedLanguageCode());
        assertEquals("English", selector.getTriggerTooltip());
    }
}
