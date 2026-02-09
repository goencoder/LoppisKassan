package se.goencoder.loppiskassan.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Standardized button component for the entire application.
 * Provides consistent styling via predefined variants.
 *
 * <h3>Variants</h3>
 * <ul>
 *   <li><b>PRIMARY</b> — Filled accent blue, white text. Main call-to-action.</li>
 *   <li><b>SECONDARY</b> — White background, dark text, subtle border. Standard actions.</li>
 *   <li><b>OUTLINE</b> — White background, accent blue text, accent border. Emphasized secondary.</li>
 *   <li><b>DANGER</b> — White background, danger red text, subtle border. Destructive actions.</li>
 *   <li><b>GHOST</b> — Transparent, muted text, no border. Minimal actions.</li>
 * </ul>
 *
 * <h3>Sizes</h3>
 * <ul>
 *   <li><b>SMALL</b> — 28px height, 12f font.</li>
 *   <li><b>MEDIUM</b> — 36px height, 13f font.</li>
 *   <li><b>LARGE</b> — 44px height, 14f font.</li>
 *   <li><b>XLARGE</b> — 50px height, 14f font, bold. Full-width bottom bar buttons.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 *     JButton save = AppButton.create("Save", AppButton.Variant.PRIMARY, AppButton.Size.MEDIUM);
 *     JButton cancel = AppButton.create("Cancel", AppButton.Variant.SECONDARY, AppButton.Size.MEDIUM);
 *     JButton delete = AppButton.create("Delete", AppButton.Variant.DANGER, AppButton.Size.MEDIUM);
 * </pre>
 */
public final class AppButton {

    private AppButton() {}

    /** Button visual variant. */
    public enum Variant {
        /** Filled accent background, white text. */
        PRIMARY,
        /** White background, dark text, border. */
        SECONDARY,
        /** White background, accent text, accent border. */
        OUTLINE,
        /** White background, red text, border. */
        DANGER,
        /** Transparent, muted text, no border. */
        GHOST
    }

    /** Button size preset. */
    public enum Size {
        SMALL(28, 12f, Font.PLAIN, 10, 4),
        MEDIUM(36, 13f, Font.PLAIN, 16, 6),
        LARGE(44, 14f, Font.PLAIN, 20, 8),
        XLARGE(50, 14f, Font.BOLD, 24, 8);

        final int height;
        final float fontSize;
        final int fontStyle;
        final int hPad;
        final int vPad;

        Size(int height, float fontSize, int fontStyle, int hPad, int vPad) {
            this.height = height;
            this.fontSize = fontSize;
            this.fontStyle = fontStyle;
            this.hPad = hPad;
            this.vPad = vPad;
        }
    }

    /**
     * Create a button with the given text, variant, and size.
     */
    public static JButton create(String text, Variant variant, Size size) {
        JButton button = new JButton(text);
        applyStyle(button, variant, size);
        return button;
    }

    /**
     * Apply variant and size styling to an existing button.
     */
    public static void applyStyle(JButton button, Variant variant, Size size) {
        // Size
        button.setFont(button.getFont().deriveFont(size.fontStyle, size.fontSize));
        button.setPreferredSize(null); // Let layout calculate width from text
        button.setMargin(new Insets(size.vPad, size.hPad, size.vPad, size.hPad));
        button.setMinimumSize(new Dimension(0, size.height));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, size.height));

        // Common
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Variant
        switch (variant) {
            case PRIMARY -> {
                button.setBackground(AppColors.ACCENT);
                button.setForeground(AppColors.WHITE);
                button.setBorder(BorderFactory.createEmptyBorder(size.vPad, size.hPad, size.vPad, size.hPad));
            }
            case SECONDARY -> {
                button.setBackground(AppColors.WHITE);
                button.setForeground(AppColors.TEXT_PRIMARY);
                button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(AppColors.BORDER, 1),
                    BorderFactory.createEmptyBorder(size.vPad, size.hPad, size.vPad, size.hPad)
                ));
            }
            case OUTLINE -> {
                button.setBackground(AppColors.WHITE);
                button.setForeground(AppColors.ACCENT);
                button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(AppColors.ACCENT, 1),
                    BorderFactory.createEmptyBorder(size.vPad, size.hPad, size.vPad, size.hPad)
                ));
            }
            case DANGER -> {
                button.setBackground(AppColors.WHITE);
                button.setForeground(AppColors.DANGER);
                button.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(AppColors.BORDER, 1),
                    BorderFactory.createEmptyBorder(size.vPad, size.hPad, size.vPad, size.hPad)
                ));
            }
            case GHOST -> {
                button.setBackground(new Color(0, 0, 0, 0));
                button.setForeground(AppColors.TEXT_MUTED);
                button.setBorder(BorderFactory.createEmptyBorder(size.vPad, size.hPad, size.vPad, size.hPad));
                button.setContentAreaFilled(false);
            }
        }
    }
}
