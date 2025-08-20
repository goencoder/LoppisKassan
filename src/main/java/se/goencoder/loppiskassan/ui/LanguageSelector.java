package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.ui.icons.FlagIcon;
import se.goencoder.loppiskassan.ui.icons.ChevronDownIcon;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LanguageSelector extends JPanel {
    public static final class LanguageItem {
        public final String code;      // "sv" or "en"
        public final String labelKey;  // "language.swedish"/"language.english"
        public final Icon   flagIcon;

        public LanguageItem(String code, String labelKey, Icon flagIcon) {
            this.code = code;
            this.labelKey = labelKey;
            this.flagIcon = flagIcon;
        }

        public String label() { return LocalizationManager.tr(labelKey); }
    }

    private final JButton trigger = new JButton();
    private final JPopupMenu menu = new JPopupMenu();
    private final Map<String, LanguageItem> items = new LinkedHashMap<>();

    public LanguageSelector() {
        setLayout(new BorderLayout());
        setOpaque(false);

        // Build models (keep insertion order for menu)
        items.put("sv", new LanguageItem("sv", "language.swedish",
                new FlagIcon("flags/se", UiKnobs.FLAG_W, UiKnobs.FLAG_H)));
        items.put("en", new LanguageItem("en", "language.english",
                new FlagIcon("flags/gb", UiKnobs.FLAG_W, UiKnobs.FLAG_H)));

        // Button visuals: only flag + chevron, compact, no text
        trigger.setFocusable(false);
        trigger.setRequestFocusEnabled(false);
        trigger.setMargin(UiKnobs.LANG_BUTTON_MARGIN);
        trigger.setFont(UiKnobs.LANG_FONT);
        trigger.setHorizontalAlignment(SwingConstants.LEFT);

        // Slightly rounded button on most LAFs
        Dimension pref = new Dimension(UiKnobs.LANG_BUTTON_MIN_W, UiKnobs.LANG_BUTTON_HEIGHT);
        trigger.setPreferredSize(pref);
        trigger.setMinimumSize(pref);

        trigger.addActionListener(e -> {
            if (!menu.isVisible()) {
                menu.show(trigger, 0, trigger.getHeight());
            } else {
                menu.setVisible(false);
            }
        });

        rebuildMenu();
        updateTriggerForCurrentLanguage();

        add(trigger, BorderLayout.CENTER);
    }

    private void rebuildMenu() {
        menu.removeAll();
        menu.setBorder(BorderFactory.createEmptyBorder(4, 0, 4, 0));
        for (LanguageItem it : items.values()) {
            JCheckBoxMenuItem mi = new JCheckBoxMenuItem(it.label());
            mi.setIcon(it.flagIcon);
            mi.setFont(UiKnobs.LANG_FONT);
            mi.setMargin(UiKnobs.LANG_POPUP_ITEM_PAD);
            mi.setIconTextGap(UiKnobs.LANG_ICON_TEXT_GAP);
            mi.setSelected(it.code.equals(currentLanguage()));
            mi.addActionListener(e -> {
                if (!it.code.equals(currentLanguage())) {
                    LocalizationManager.setLanguage(it.code);
                    updateTriggerForCurrentLanguage();
                    // keep menu open only briefly so click feedback is visible
                }
                menu.setVisible(false);
            });
            // Keep rows consistent height
            mi.setPreferredSize(new Dimension(
                    Math.max(mi.getPreferredSize().width, 160),
                    UiKnobs.LANG_POPUP_ROW_HEIGHT + UiKnobs.LANG_POPUP_ITEM_PAD.top + UiKnobs.LANG_POPUP_ITEM_PAD.bottom
            ));
            menu.add(mi);
        }
    }

    private void updateTriggerForCurrentLanguage() {
        LanguageItem active = items.getOrDefault(currentLanguage(), items.get("sv"));
        // Compose flag + chevron
        trigger.setIcon(active.flagIcon);
        trigger.setText(null); // collapsed state: no label
        trigger.setDisabledIcon(active.flagIcon);
        trigger.setToolTipText(active.label());

        // Place a chevron on the right via compound icon (using button's "disabled" area is overkill).
        // Simpler: set a right-side icon via HTML gap — or draw in paintComponent.
        // We'll add a small secondary icon via setRolloverIcon for visual hint.
        trigger.setRolloverIcon(new CompoundIcon(active.flagIcon, new ChevronDownIcon(10, 10)));
        trigger.setIcon(new CompoundIcon(active.flagIcon, new ChevronDownIcon(10, 10)));
        // Ensure menu reflects selection after i18n change
        rebuildMenu();
        revalidate();
        repaint();
    }

    private static String currentLanguage() {
        return LocalizationManager.getLanguage();
    }

    public void selectLanguage(String code) {
        if (code == null) {
            return;
        }
        if (!code.equals(currentLanguage())) {
            LocalizationManager.setLanguage(code);
            updateTriggerForCurrentLanguage();
        }
    }

    public String getSelectedLanguageCode() {
        return currentLanguage();
    }

    public String getTriggerTooltip() {
        return trigger.getToolTipText();
    }

    // Helper to paint two icons side-by-side (flag + chevron)
    private static final class CompoundIcon implements Icon {
        private final Icon left, right;
        private final int gap;

        CompoundIcon(Icon left, Icon right) { this(left, right, 6); }
        CompoundIcon(Icon left, Icon right, int gap) { this.left = left; this.right = right; this.gap = gap; }

        @Override public int getIconWidth() { return left.getIconWidth() + gap + right.getIconWidth(); }
        @Override public int getIconHeight() { return Math.max(left.getIconHeight(), right.getIconHeight()); }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            int cy = y + (getIconHeight() - left.getIconHeight()) / 2;
            left.paintIcon(c, g, x, cy);
            int x2 = x + left.getIconWidth() + gap;
            cy = y + (getIconHeight() - right.getIconHeight()) / 2;
            right.paintIcon(c, g, x2, cy);
        }
    }
}
