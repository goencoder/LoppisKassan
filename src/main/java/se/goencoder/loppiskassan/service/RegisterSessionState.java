package se.goencoder.loppiskassan.service;

/**
 * Lifecycle states for a cashier register session (ILP-003).
 *
 * <pre>
 *   OPEN ──► CLOSE_REQUESTED ──► CLOSED
 *     └──────────────────────────────► FORCED_CLOSED  (admin override)
 * </pre>
 *
 * {@code CLOSED} and {@code FORCED_CLOSED} are terminal; a new session must be opened.
 */
public enum RegisterSessionState {
    OPEN,
    CLOSE_REQUESTED,
    CLOSED,
    FORCED_CLOSED
}
