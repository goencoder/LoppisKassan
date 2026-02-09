package se.goencoder.loppiskassan.ui;

import java.awt.Color;

/**
 * Centralized color palette for the entire application.
 * ALL colors used in UI code MUST come from this class — no hardcoded hex values elsewhere.
 *
 * Palette inspired by Tailwind CSS neutral/blue scale for a clean, modern look.
 */
public final class AppColors {

    private AppColors() {}

    // ── Backgrounds ──
    /** Pure white — primary background for panels, cards, content areas. */
    public static final Color WHITE = Color.WHITE;

    /** Very light gray — sidebar, statusbar, subtle card backgrounds. */
    public static final Color SURFACE = new Color(0xF7FAFC);

    /** Light blue-gray — input field backgrounds, card backgrounds. */
    public static final Color FIELD_BG = new Color(0xF0F4F8);

    /** Selected card background — very light blue. */
    public static final Color SELECTED_BG = new Color(0xE8F0FE);

    // ── Text ──
    /** Primary text — headings, labels, body text. */
    public static final Color TEXT_PRIMARY = new Color(0x2D3748);

    /** Secondary text — section headers, secondary labels. */
    public static final Color TEXT_SECONDARY = new Color(0x4A5568);

    /** Muted text — descriptions, hints, placeholders, disabled text. */
    public static final Color TEXT_MUTED = new Color(0x718096);

    // ── Borders & Separators ──
    /** Standard border/separator/divider color. */
    public static final Color BORDER = new Color(0xE2E8F0);

    /** Light border — pie chart platform, subtle dividers. */
    public static final Color BORDER_LIGHT = new Color(0xCBD5E0);

    /** Medium gray — pie chart market owner segment. */
    public static final Color BORDER_MEDIUM = new Color(0xA0AEC0);

    // ── Accent (Brand / Primary Action) ──
    /** Primary accent — buttons, links, selected indicators, focus rings. */
    public static final Color ACCENT = new Color(0x4A90D9);

    /** Accent with alpha for subtle overlays (card selection outlines). */
    public static final Color ACCENT_TRANSLUCENT = new Color(0x4A90D9, true);

    // ── Semantic ──
    /** Success — positive change values, confirmation states. */
    public static final Color SUCCESS = new Color(0x4CAF50);

    /** Danger — errors, negative change values, destructive actions. */
    public static final Color DANGER = new Color(0xE53E3E);

    /** Danger secondary — mode icon tint. */
    public static final Color DANGER_SECONDARY = new Color(0xE05A5A);

    // ── Icons ──
    /** Icon stroke color — chevrons, small icons. */
    public static final Color ICON_STROKE = new Color(0x3C3C3C);

    // ── Dialog/Preview ──
    /** Preview text color. */
    public static final Color PREVIEW_TEXT = new Color(0x505050);

    /** Preview border. */
    public static final Color PREVIEW_BORDER = new Color(0xC8C8C8);

    /** Preview background. */
    public static final Color PREVIEW_BG = new Color(0xF5F5F5);
}
