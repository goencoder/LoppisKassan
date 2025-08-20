package se.goencoder.loppiskassan.ui;

import java.awt.*;

public final class UiKnobs {
    private UiKnobs() {}

    // Language selector sizing
    public static final int LANG_SELECTOR_MIN_WIDTH = 160; // wide enough for longest label
    public static final int LANG_SELECTOR_HEIGHT    = 32;

    // Flag icon box (kept non-square; the icon renderer preserves AR inside this box)
    public static final int FLAG_W = 24;  // logical px
    public static final int FLAG_H = 16;  // 3:2 ratio

    // Spacing & typography
    public static final int LANG_ICON_TEXT_GAP = 8;
    public static final Insets LANG_CELL_PADDING = new Insets(4, 8, 4, 8);
    public static final Font LANG_FONT = new Font("SansSerif", Font.PLAIN, 13);

    // Popup list row height (enough for icon + padding)
    public static final int LANG_LIST_ROW_HEIGHT = 24;
}

