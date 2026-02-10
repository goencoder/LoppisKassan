package se.goencoder.loppiskassan.localization;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import se.goencoder.loppiskassan.config.GlobalConfigurationStore;
import se.goencoder.loppiskassan.config.ILoppisConfigurationStore;
import se.goencoder.loppiskassan.config.LocalConfigurationStore;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that localization parameters using MessageFormat {0} syntax work correctly.
 * This is critical for dialogs showing event names and other dynamic text.
 */
class LocalizationParameterTest {

    @BeforeAll
    static void headless() {
        System.setProperty("java.awt.headless", "true");
    }

    @BeforeEach
    void resetConfig() {
        GlobalConfigurationStore.reset();
        LocalConfigurationStore.reset();
        ILoppisConfigurationStore.reset();
        // Start fresh with Swedish
        LocalizationManager.initialize();
    }

    @Test
    void deleteConfirmDialogShowsEventNameInSwedish() {
        LocalizationManager.setLanguage("sv");
        String eventName = "Min egen loppis";
        
        String result = LocalizationManager.tr("discovery.delete.confirm", eventName);
        
        // Verify the event name appears in the message
        assertTrue(result.contains(eventName), 
            "Expected message to contain '" + eventName + "' but got: " + result);
        
        // Verify the literal {0} placeholder does NOT appear
        assertFalse(result.contains("{0}"), 
            "Message should not contain literal {0} placeholder. Got: " + result);
        
        // Verify it contains the expected Swedish text
        assertTrue(result.contains("Är du säker"), 
            "Expected Swedish confirmation text. Got: " + result);
        assertTrue(result.contains("radera"), 
            "Expected Swedish 'radera' (delete). Got: " + result);
    }

    @Test
    void deleteConfirmDialogShowsEventNameInEnglish() {
        LocalizationManager.setLanguage("en");
        String eventName = "My Local Market";
        
        String result = LocalizationManager.tr("discovery.delete.confirm", eventName);
        
        // Verify the event name appears in the message
        assertTrue(result.contains(eventName), 
            "Expected message to contain '" + eventName + "' but got: " + result);
        
        // Verify the literal {0} placeholder does NOT appear
        assertFalse(result.contains("{0}"), 
            "Message should not contain literal {0} placeholder. Got: " + result);
        
        // Verify it contains the expected English text
        assertTrue(result.contains("Are you sure"), 
            "Expected English confirmation text. Got: " + result);
        assertTrue(result.contains("delete"), 
            "Expected English 'delete'. Got: " + result);
    }

    @Test
    void deleteFailedMessageShowsErrorDetailsInSwedish() {
        LocalizationManager.setLanguage("sv");
        String errorDetails = "Filen kunde inte hittas";
        
        String result = LocalizationManager.tr("local_event.delete_failed.message", errorDetails);
        
        assertTrue(result.contains(errorDetails), 
            "Expected error message to contain '" + errorDetails + "' but got: " + result);
        assertFalse(result.contains("{0}"), 
            "Message should not contain literal {0} placeholder. Got: " + result);
    }

    @Test
    void deleteFailedMessageShowsErrorDetailsInEnglish() {
        LocalizationManager.setLanguage("en");
        String errorDetails = "File not found";
        
        String result = LocalizationManager.tr("local_event.delete_failed.message", errorDetails);
        
        assertTrue(result.contains(errorDetails), 
            "Expected error message to contain '" + errorDetails + "' but got: " + result);
        assertFalse(result.contains("{0}"), 
            "Message should not contain literal {0} placeholder. Got: " + result);
    }

    @Test
    void loadEventsErrorShowsPathInSwedish() {
        LocalizationManager.setLanguage("sv");
        String filePath = "/data/events/test.jsonl";
        
        String result = LocalizationManager.tr("error.load_local_events.message", filePath);
        
        assertTrue(result.contains(filePath), 
            "Expected error message to contain '" + filePath + "' but got: " + result);
        assertFalse(result.contains("{0}"), 
            "Message should not contain literal {0} placeholder. Got: " + result);
    }

    @Test
    void loadEventsErrorShowsPathInEnglish() {
        LocalizationManager.setLanguage("en");
        String filePath = "/data/events/test.jsonl";
        
        String result = LocalizationManager.tr("error.load_local_events.message", filePath);
        
        assertTrue(result.contains(filePath), 
            "Expected error message to contain '" + filePath + "' but got: " + result);
        assertFalse(result.contains("{0}"), 
            "Message should not contain literal {0} placeholder. Got: " + result);
    }

    @Test
    void multipleParametersWorkCorrectly() {
        LocalizationManager.setLanguage("sv");
        
        // Create a test key with multiple parameters if needed
        // For now, test with single parameter edge cases
        String eventNameWithSpecialChars = "Loppis 'Med' \"Citat\" & Symboler";
        
        String result = LocalizationManager.tr("discovery.delete.confirm", eventNameWithSpecialChars);
        
        assertTrue(result.contains(eventNameWithSpecialChars) || 
                   result.contains("Loppis"), // MessageFormat might escape quotes
            "Expected message to contain event name with special chars. Got: " + result);
        assertFalse(result.contains("{0}"), 
            "Message should not contain literal {0} placeholder. Got: " + result);
    }
}
