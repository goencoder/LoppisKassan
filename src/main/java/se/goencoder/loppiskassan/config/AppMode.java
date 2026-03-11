package se.goencoder.loppiskassan.config;

/**
 * Application operating mode, chosen at startup via the splash screen.
 * <ul>
 *   <li>{@link #LOCAL} — fully offline; no iLoppis server interaction.</li>
 *   <li>{@link #ILOPPIS} — online mode backed by the iLoppis API.</li>
 * </ul>
 */
public enum AppMode {
    LOCAL,
    ILOPPIS
}
