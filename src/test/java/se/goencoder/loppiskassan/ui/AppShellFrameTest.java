package se.goencoder.loppiskassan.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppShellFrameTest {

    @Test
    void cleanActiveSessionDoesNotRequireConfirmationWhenCashierCodeIsSaved() {
        assertFalse(AppShellFrame.shouldConfirmSessionClose(true, true));
    }

    @Test
    void cleanActiveSessionRequiresConfirmationWhenCashierCodeIsNotSaved() {
        assertTrue(AppShellFrame.shouldConfirmSessionClose(true, false));
    }

    @Test
    void inactiveSessionNeverRequiresCloseConfirmation() {
        assertFalse(AppShellFrame.shouldConfirmSessionClose(false, false));
    }
}
