package se.goencoder.loppiskassan.ui;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.goencoder.loppiskassan.config.GlobalConfigurationStore;
import se.goencoder.loppiskassan.config.LocalConfigurationStore;
import se.goencoder.loppiskassan.config.ILoppisConfigurationStore;
import se.goencoder.loppiskassan.localization.LocalizationManager;

import static org.junit.jupiter.api.Assertions.*;

class LanguageSelectorTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    @BeforeEach
    void resetConfig() {
        GlobalConfigurationStore.reset();
        LocalConfigurationStore.reset();
        ILoppisConfigurationStore.reset();
        // Explicitly set language to Swedish for tests
        GlobalConfigurationStore.setLanguage("sv");
    }

    @Test
    void selectingLanguagePersistsInConfig() {
        LanguageSelector selector = new LanguageSelector();
        assertEquals("sv", selector.getSelectedLanguageCode());

        selector.selectLanguage("en");
        assertEquals("en", selector.getSelectedLanguageCode());
        assertEquals("en", GlobalConfigurationStore.getLanguage());
    }

    @Test
    void usesPersistedLanguageOnInit() {
        GlobalConfigurationStore.setLanguage("en");

        LanguageSelector selector = new LanguageSelector();
        assertEquals("en", selector.getSelectedLanguageCode());
        assertEquals("English", selector.getTriggerTooltip());
    }
}
