package se.goencoder.loppiskassan.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.utils.UlidGenerator;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages the register session lifecycle state machine for the desktop cashier (ILP-003-05, ILP-003-06).
 *
 * <p>The session is persisted to disk at
 * {@code ~/.loppiskassan/events/{eventId}/register_session.json} so that state survives
 * crashes and power-off.  All mutations are written atomically via a temp-file + rename.</p>
 *
 * <p>Valid transitions:
 * <pre>
 *   OPEN ──► CLOSE_REQUESTED ──► CLOSED
 *     └──────────────────────────────► FORCED_CLOSED
 * </pre>
 * {@code CLOSED} and {@code FORCED_CLOSED} are terminal; call {@link #openSession(String, String)}
 * to start a new session.
 * </p>
 *
 * <p>This class is thread-safe with a simple {@code synchronized} monitor.</p>
 */
public class RegisterSessionManager {

    private static final Logger log = Logger.getLogger(RegisterSessionManager.class.getName());
    private static final String SESSION_FILE_NAME = "register_session.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static RegisterSessionManager instance;

    /**
     * Persisted session data.
     */
    public static class SessionData {
        public String sessionId;
        public String eventId;
        public String registerId;
        public String deviceId;
        public RegisterSessionState state;
        public String openedAt;
        public String lastSyncAt;
        public String closeRequestedAt;
        public String closedAt;
    }

    private SessionData current;

    private RegisterSessionManager() {}

    public static synchronized RegisterSessionManager getInstance() {
        if (instance == null) {
            instance = new RegisterSessionManager();
        }
        return instance;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public lifecycle API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Open a new session for the given event and register.  Safe to call even when no
     * prior session exists or the previous session was terminal (CLOSED / FORCED_CLOSED).
     *
     * @param eventId    the active event
     * @param registerId human-readable register name (e.g. "Kassan 1")
     */
    public synchronized SessionData openSession(String eventId, String registerId) {
        if (eventId == null || eventId.isBlank() || registerId == null || registerId.isBlank()) {
            log.warning("Refusing to open register session with missing eventId/registerId");
            return null;
        }
        if (current != null) {
            if (current.state == RegisterSessionState.OPEN && eventId.equals(current.eventId)) {
                log.info("Register session already active; reusing existing session " + current.sessionId);
                return current;
            }
            if (current.state == RegisterSessionState.OPEN && !eventId.equals(current.eventId)) {
                log.info("Active register session " + current.sessionId
                        + " belongs to event " + current.eventId
                        + "; closing it before opening a new session for event " + eventId);
                current.state = RegisterSessionState.FORCED_CLOSED;
                current.closedAt = Instant.now().toString();
                persist(current.eventId, current);
            }
        }
        SessionData s = new SessionData();
        s.sessionId = UlidGenerator.generate();
        s.eventId = eventId;
        s.registerId = registerId;
        s.deviceId = DEVICE_ID;
        s.state = RegisterSessionState.OPEN;
        s.openedAt = Instant.now().toString();
        current = s;
        persist(eventId, s);
        log.info("Register session opened: " + s.sessionId + " register=" + registerId);
        return s;
    }

    /**
     * Record a sync event (updates {@code lastSyncAt}).
     */
    public synchronized void recordSync() {
        if (current == null || isTerminal(current.state)) return;
        current.lastSyncAt = Instant.now().toString();
        persist(current.eventId, current);
    }

    /**
     * Transition to {@code CLOSE_REQUESTED}.  Call when the cashier initiates close.
     *
     * @return true if transition was allowed, false if already terminal or no session
     */
    public synchronized boolean requestClose() {
        if (current == null) return false;
        if (current.state != RegisterSessionState.OPEN) return false;
        current.state = RegisterSessionState.CLOSE_REQUESTED;
        current.closeRequestedAt = Instant.now().toString();
        persist(current.eventId, current);
        log.info("Register close requested: " + current.sessionId);
        return true;
    }

    /**
     * Transition to {@code CLOSED} once all pending items are synced and server has acknowledged.
     *
     * @return true if transition succeeded
     */
    public synchronized boolean confirmClose() {
        if (current == null) return false;
        if (current.state != RegisterSessionState.CLOSE_REQUESTED) return false;
        current.state = RegisterSessionState.CLOSED;
        current.closedAt = Instant.now().toString();
        persist(current.eventId, current);
        log.info("Register session closed: " + current.sessionId);
        return true;
    }

    /**
     * Force-close the session (admin override path).  Terminal; cannot be reopened.
     */
    public synchronized void forceClose(String reason) {
        if (current == null) return;
        current.state = RegisterSessionState.FORCED_CLOSED;
        current.closedAt = Instant.now().toString();
        persist(current.eventId, current);
        log.warning("Register session force-closed: " + current.sessionId + " reason=" + reason);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Queries
    // ─────────────────────────────────────────────────────────────────────────

    /** @return the current session, or {@code null} if none is loaded. */
    public synchronized SessionData getCurrent() {
        return current;
    }

    /** @return true if the current session is in {@code OPEN} or {@code CLOSE_REQUESTED} state. */
    public synchronized boolean isSessionActive() {
        return current != null
                && (current.state == RegisterSessionState.OPEN
                || current.state == RegisterSessionState.CLOSE_REQUESTED);
    }

    /**
     * Load (or recover) the persisted session for the given event.  Call on app start.
     *
     * @return the recovered session, or {@code null} if none existed.
     */
    public synchronized SessionData loadOrRecover(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            current = null;
            return null;
        }
        Path path = getSessionPath(eventId);
        if (!Files.exists(path)) {
            current = null;
            log.info("No persisted register session found for event " + eventId);
            return null;
        }
        try {
            String json = Files.readString(path);
            SessionData s = GSON.fromJson(json, SessionData.class);
            if (s == null || s.sessionId == null || s.eventId == null || s.registerId == null || s.state == null) {
                current = null;
                log.warning("Corrupt register session file — discarding");
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.log(Level.WARNING, "Failed to delete corrupt register session file", e);
                }
                return null;
            }
            if (!eventId.equals(s.eventId)) {
                log.warning("Register session event mismatch; expected " + eventId + " got " + s.eventId);
                return null;
            }
            current = s;
            log.info("Recovered register session: " + s.sessionId + " state=" + s.state);
            return s;
        } catch (IOException e) {
            current = null;
            log.log(Level.WARNING, "Failed to read register session file", e);
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Persistence helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void persist(String eventId, SessionData s) {
        Path tmp = null;
        try {
            Path path = getSessionPath(eventId);
            Files.createDirectories(path.getParent());
            tmp = path.resolveSibling(SESSION_FILE_NAME + ".tmp");
            Files.writeString(tmp, GSON.toJson(s));
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException e) {
            log.log(Level.WARNING, "Failed to persist register session", e);
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException cleanupException) {
                    log.log(Level.WARNING, "Failed to clean up temporary register session file", cleanupException);
                }
            }
        }
    }

    private static Path getSessionPath(String eventId) {
        return LocalEventPaths.getEventDir(eventId).resolve(SESSION_FILE_NAME);
    }

    private static boolean isTerminal(RegisterSessionState state) {
        return state == RegisterSessionState.CLOSED || state == RegisterSessionState.FORCED_CLOSED;
    }

    private static final String DEVICE_ID = resolveDeviceId();

    private static String resolveDeviceId() {
        String configured = System.getenv("LOPPISKASSAN_DEVICE_ID");
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String hostname = System.getenv("HOSTNAME");
        if (hostname == null || hostname.isBlank()) {
            hostname = System.getenv("COMPUTERNAME");
        }
        if (hostname == null || hostname.isBlank()) {
            hostname = System.getenv("HOST");
        }
        if (hostname != null && !hostname.isBlank()) {
            return hostname;
        }
        try {
            return java.net.InetAddress.getLoopbackAddress().getHostName();
        } catch (Exception e) {
            return "unknown-device";
        }
    }
}
