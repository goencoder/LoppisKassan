package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.localization.LocalizationManager;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.InputStream;
import java.util.Objects;

/**
 * Språk-dropdown med PNG flaggor och ✓ för valt språk.
 * Anropar LocalizationManager.setLanguage(code) vid val och uppdaterar UI.
 */
public class LanguageSelector extends JPanel {

    public static final class LocaleOption {
        public final String code;   // "sv", "en"
        public final String labelKey; // t.ex. "language.swedish"
        public final String flagFile; // "se@1x.png", "gb@1x.png"
        public LocaleOption(String code, String labelKey, String flagFile) {
            this.code = code;
            this.labelKey = labelKey;
            this.flagFile = flagFile;
        }
        @Override public String toString() { return code; }
    }

    private final JComboBox<LocaleOption> combo;

    public LanguageSelector() {
        setOpaque(false);
        setLayout(new BorderLayout());

        // Tillgängliga språk här – lägg till fler vid behov
        LocaleOption[] options = new LocaleOption[] {
                new LocaleOption("sv", "language.swedish", "se@1x.png"),
                new LocaleOption("en", "language.english", "gb@1x.png")
        };

        combo = new JComboBox<>(options);
        combo.setRenderer(new FlagRenderer(combo));
        combo.setFocusable(false);
        combo.setBorder(new EmptyBorder(2, 6, 2, 6));
        // Välj default (sv) – eller läs från ev. konfiguration om du vill persistera val
        combo.setSelectedIndex(0);

        combo.addActionListener(e -> {
            LocaleOption sel = (LocaleOption) combo.getSelectedItem();
            if (sel != null) {
                LocalizationManager.setLanguage(sel.code);
                // Ev. spara i konfiguration här om du vill persistera språkvalet
            }
        });

        add(combo, BorderLayout.CENTER);
    }

    /**
     * Custom renderer som ritar PNG flagga + (valfritt) text och ✓ på det aktuella valet i popup-listan.
     * I stängd vy kan vi dölja text för ett rent "flag + caret"-utseende.
     */
    private static class FlagRenderer extends DefaultListCellRenderer {
        private final JComboBox<LocaleOption> owner;
        FlagRenderer(JComboBox<LocaleOption> owner) { this.owner = owner; }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {
            JLabel lbl = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            lbl.setBorder(new EmptyBorder(4, 8, 4, 8));
            lbl.setOpaque(true);

            if (!(value instanceof LocaleOption opt)) {
                lbl.setText("");
                lbl.setIcon(null);
                return lbl;
            }

            boolean closedView = (index == -1);
            boolean showText = closedView ? UiKnobs.LANG_BUTTON_SHOW_TEXT : UiKnobs.LANG_POPUP_SHOW_TEXT;

            // Load PNG flag icon
            Icon icon = loadPngFlag(opt.flagFile, UiKnobs.LANG_ICON_SIZE_PX);
            String text = LocalizationManager.tr(opt.labelKey);

            lbl.setIcon(icon);
            lbl.setText(showText ? "  " + text : "");

            // Add ✓ to the selected row in the popup only
            if (!closedView) {
                Object selected = owner.getSelectedItem();
                if (Objects.equals(selected, opt)) {
                    lbl.setText("✓  " + lbl.getText());
                }
            }

            return lbl;
        }
    }

    private static Icon loadPngFlag(String flagFile, int sizePx) {
        String path = UiKnobs.LANG_FLAGS_PATH + flagFile;
        try (InputStream is = LanguageSelector.class.getResourceAsStream(path)) {
            if (is == null) {
                System.err.println("Could not find flag file: " + path);
                return null;
            }
            byte[] bytes = is.readAllBytes();
            Image img = Toolkit.getDefaultToolkit().createImage(bytes);
            Image scaled = img.getScaledInstance(sizePx, sizePx, Image.SCALE_SMOOTH);
            return new ImageIcon(scaled);
        } catch (Exception e) {
            System.err.println("Error loading flag file " + path + ": " + e.getMessage());
            return null;
        }
    }
}
