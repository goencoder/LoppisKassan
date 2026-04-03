package se.goencoder.loppiskassan.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ILP-003-05 / ILP-003-06: Unit tests for RegisterSessionManager state machine.
 *
 * Each test resets the singleton so tests are independent.
 */
class RegisterSessionManagerTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void resetSingleton() throws Exception {
        Field f = RegisterSessionManager.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);

        // Point LocalEventPaths base dir to tempDir so no real FS side-effects.
        System.setProperty("loppiskassan.base.dir", tempDir.toString());
    }

    @AfterEach
    void clearSysProperty() {
        System.clearProperty("loppiskassan.base.dir");
    }

    // ─────────────────────────────────────────────────────── open

    @Test
    void openSession_setsStateOpen() {
        var mgr = RegisterSessionManager.getInstance();
        var session = mgr.openSession("evt-1", "Kassan 1");

        assertEquals(RegisterSessionState.OPEN, session.state);
        assertNotNull(session.sessionId);
        assertEquals("Kassan 1", session.registerId);
        assertEquals("evt-1", session.eventId);
        assertTrue(mgr.isSessionActive());
    }

    @Test
    void openSession_createsNewSessionAfterClosed() {
        var mgr = RegisterSessionManager.getInstance();
        var first = mgr.openSession("evt-1", "K1");
        mgr.requestClose();
        mgr.confirmClose();

        var second = mgr.openSession("evt-1", "K1");
        assertNotEquals(first.sessionId, second.sessionId);
        assertEquals(RegisterSessionState.OPEN, second.state);
    }

    // ─────────────────────────────────────────────────────── close request

    @Test
    void requestClose_transitionsToCloseRequested() {
        var mgr = RegisterSessionManager.getInstance();
        mgr.openSession("evt-1", "K1");

        boolean result = mgr.requestClose();

        assertTrue(result);
        assertEquals(RegisterSessionState.CLOSE_REQUESTED, mgr.getCurrent().state);
        assertTrue(mgr.isSessionActive());
    }

    @Test
    void requestClose_idempotent_returnsFalseIfAlreadyRequested() {
        var mgr = RegisterSessionManager.getInstance();
        mgr.openSession("evt-1", "K1");
        mgr.requestClose();

        boolean secondCall = mgr.requestClose();

        assertFalse(secondCall);
    }

    @Test
    void requestClose_returnsFalseWhenNoSession() {
        var mgr = RegisterSessionManager.getInstance();
        assertFalse(mgr.requestClose());
    }

    // ─────────────────────────────────────────────────────── confirm close

    @Test
    void confirmClose_transitionsToClosed() {
        var mgr = RegisterSessionManager.getInstance();
        mgr.openSession("evt-1", "K1");
        mgr.requestClose();

        boolean result = mgr.confirmClose();

        assertTrue(result);
        assertEquals(RegisterSessionState.CLOSED, mgr.getCurrent().state);
        assertFalse(mgr.isSessionActive());
    }

    @Test
    void confirmClose_requiresCloseRequestedFirst() {
        var mgr = RegisterSessionManager.getInstance();
        mgr.openSession("evt-1", "K1");

        // Cannot confirm close without first requesting it
        boolean result = mgr.confirmClose();

        assertFalse(result);
        assertEquals(RegisterSessionState.OPEN, mgr.getCurrent().state);
    }

    // ─────────────────────────────────────────────────────── force close

    @Test
    void forceClose_isTerminal() {
        var mgr = RegisterSessionManager.getInstance();
        mgr.openSession("evt-1", "K1");
        mgr.forceClose("admin override");

        assertEquals(RegisterSessionState.FORCED_CLOSED, mgr.getCurrent().state);
        assertFalse(mgr.isSessionActive());
    }

    @Test
    void forceClose_fromCloseRequested_isTerminal() {
        var mgr = RegisterSessionManager.getInstance();
        mgr.openSession("evt-1", "K1");
        mgr.requestClose();
        mgr.forceClose("timed out");

        assertEquals(RegisterSessionState.FORCED_CLOSED, mgr.getCurrent().state);
    }

    // ─────────────────────────────────────────────────────── persistence / recovery

    @Test
    void openSession_persistsStateToDisk() throws Exception {
        var mgr = RegisterSessionManager.getInstance();
        mgr.openSession("evt-persist", "K1");

        // Reset singleton to simulate restart and recover
        Field f = RegisterSessionManager.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);

        var mgr2 = RegisterSessionManager.getInstance();
        var recovered = mgr2.loadOrRecover("evt-persist");

        assertNotNull(recovered);
        assertEquals(RegisterSessionState.OPEN, recovered.state);
        assertEquals("K1", recovered.registerId);
    }

    @Test
    void loadOrRecover_returnsNullWhenNoFilePersisted() {
        var mgr = RegisterSessionManager.getInstance();
        var recovered = mgr.loadOrRecover("evt-does-not-exist");
        assertNull(recovered);
    }

    // ─────────────────────────────────────────────────────── no-session guard

    @Test
    void isSessionActive_falseWithNoSession() {
        assertFalse(RegisterSessionManager.getInstance().isSessionActive());
    }

    @Test
    void getCurrent_nullWithNoSession() {
        assertNull(RegisterSessionManager.getInstance().getCurrent());
    }
}
