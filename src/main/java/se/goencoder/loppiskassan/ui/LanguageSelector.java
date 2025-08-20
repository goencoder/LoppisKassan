package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.ui.icons.FlagIcon;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxRenderer;
import java.awt.*;

public final class LanguageSelector extends JPanel {
    public static final class LanguageItem {
        public final String code;      // "sv" or "en"
        public final String labelKey;  // "language.swedish" / "language.english"
        public final Icon icon;

        public LanguageItem(String code, String labelKey, Icon icon) {
            this.code = code;
            this.labelKey = labelKey;
            this.icon = icon;
        }

        @Override public String toString() { return LocalizationManager.tr(labelKey); }
    }

    private final JComboBox<LanguageItem> combo;

    public LanguageSelector() {
        setLayout(new BorderLayout());

        LanguageItem sv = new LanguageItem(
                "sv", "language.swedish",
                new FlagIcon("flags/se", UiKnobs.FLAG_W, UiKnobs.FLAG_H)
        );
        LanguageItem en = new LanguageItem(
                "en", "language.english",
                new FlagIcon("flags/gb", UiKnobs.FLAG_W, UiKnobs.FLAG_H)
        );

        combo = new JComboBox<>(new DefaultComboBoxModel<>(new LanguageItem[]{ sv, en }));
        combo.setFont(UiKnobs.LANG_FONT);
        combo.setMaximumRowCount(6);
        combo.setRenderer(new FlagRenderer());
        combo.setPrototypeDisplayValue(en); // ensures width for longest label
        combo.setPreferredSize(new Dimension(
                Math.max(UiKnobs.LANG_SELECTOR_MIN_WIDTH, combo.getPreferredSize().width),
                UiKnobs.LANG_SELECTOR_HEIGHT
        ));

        String cur = getCurrentLanguageCode();
        combo.setSelectedItem("en".equals(cur) ? en : sv);

        combo.addActionListener(e -> {
            LanguageItem li = (LanguageItem) combo.getSelectedItem();
            if (li != null) LocalizationManager.setLanguage(li.code);
        });

        add(combo, BorderLayout.CENTER);
    }

    private static String getCurrentLanguageCode() {
        return "sv"; // default
    }

    private static final class FlagRenderer extends BasicComboBoxRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {

            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            LanguageItem item = (LanguageItem) value;
            setIcon(item.icon);
            setText(LocalizationManager.tr(item.labelKey));
            setFont(UiKnobs.LANG_FONT);
            setBorder(BorderFactory.createEmptyBorder(
                    UiKnobs.LANG_CELL_PADDING.top,
                    UiKnobs.LANG_CELL_PADDING.left,
                    UiKnobs.LANG_CELL_PADDING.bottom,
                    UiKnobs.LANG_CELL_PADDING.right
            ));
            setIconTextGap(UiKnobs.LANG_ICON_TEXT_GAP);

            if (list != null && list.getFixedCellHeight() != UiKnobs.LANG_LIST_ROW_HEIGHT) {
                list.setFixedCellHeight(UiKnobs.LANG_LIST_ROW_HEIGHT);
            }

            return this;
        }
    }
}

