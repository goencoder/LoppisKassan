package se.goencoder.loppiskassan.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;

/**
 * Single place for all UI look & feel knobs.
 * Tweak the public static fields below to control the whole app style.
 *
 * System props (optional overrides):
 *  -Diloppis.theme=dark|light
 *  -Diloppis.ui.scale=125   (percent; sets flatlaf.uiScale)
 */
public final class Theme {

    // ---- Control knobs (edit these) ----------------------------------------
    public static boolean FOLLOW_OS = false;      // true requires flatlaf-system-extensions (not used yet)
    public static final boolean DARK_DEFAULT = false;   // app default if FOLLOW_OS is false or not supported

    public static final int COMPONENT_ARC = 16;
    public static final int BUTTON_ARC    = 20;
    public static final int TEXT_ARC      = 12;
    public static final int FOCUS_WIDTH   = 1;

    public static final boolean SCROLLBAR_BUTTONS = false;
    public static final boolean TABLE_STRIPES     = true;

    /** Optional brand accent for focus & selection; set to null to use default. */
    public static final Color ACCENT = null; // e.g. new Color(0x228BE6);

    /** Optional global UI scale in percent (0 = off). Can also be set via -Diloppis.ui.scale=125 */
    public static final int UI_SCALE_PERCENT = Integer.getInteger("iloppis.ui.scale", 0);
    // ------------------------------------------------------------------------

    private Theme() {}

    /** Install LAF and apply all UI defaults. Call once before creating any Swing components. */
    public static void install() {
        // Optional scaling
        if (UI_SCALE_PERCENT > 0) {
            System.setProperty("flatlaf.uiScale", UI_SCALE_PERCENT + "%");
        }

        // Determine dark/light via system prop or defaults
        String forced = System.getProperty("iloppis.theme", null); // "dark"|"light"|null
        boolean useDark = forced != null ? forced.equalsIgnoreCase("dark") : DARK_DEFAULT;

        try {
            UIManager.setLookAndFeel(useDark ? new FlatDarkLaf() : new FlatLightLaf());
        } catch (UnsupportedLookAndFeelException ignore) {
            // Keep whatever LAF is available; still apply some defaults below.
        }

        // Global style tweaks
        UIManager.put("Component.arc", COMPONENT_ARC);
        UIManager.put("Button.arc", BUTTON_ARC);
        UIManager.put("TextComponent.arc", TEXT_ARC);
        UIManager.put("Component.focusWidth", FOCUS_WIDTH);

        UIManager.put("ScrollBar.showButtons", SCROLLBAR_BUTTONS);

        UIManager.put("Table.showHorizontalLines", false);
        UIManager.put("Table.showVerticalLines", false);
        if (TABLE_STRIPES) {
            // FlatLaf provides alternate row coloring automatically; keep default striping on.
            // (No explicit setting needed; left here as a reminder knob.)
        }

        if (ACCENT != null) {
            UIManager.put("Component.focusColor", ACCENT);
            UIManager.put("Button.default.focusColor", ACCENT);
            UIManager.put("ScrollBar.thumbHoverBorderColor", ACCENT);
        }
    }

    /**
     * Switch theme at runtime, if needed later (not wired to UI yet).
     * Call with `true` for dark, `false` for light.
     */
    public static void switchTheme(Window root, boolean dark) {
        try {
            UIManager.setLookAndFeel(dark ? new FlatDarkLaf() : new FlatLightLaf());
            SwingUtilities.updateComponentTreeUI(root);
            root.pack();
        } catch (UnsupportedLookAndFeelException ignore) { }
    }
}

