package se.goencoder.loppiskassan.ui;

import java.awt.*;

public final class UiKnobs {
    private UiKnobs() {}

    // Language button (collapsed) – compact
    public static final int LANG_BUTTON_HEIGHT   = 28;
    public static final int LANG_BUTTON_MIN_W    = 56; // flag + chevron
    public static final Insets LANG_BUTTON_MARGIN = new Insets(2, 6, 2, 6);

    // Popup list
    public static final int LANG_POPUP_ROW_HEIGHT = 24;
    public static final int LANG_POPUP_ITEM_MIN_W = 160;
    public static final Insets LANG_POPUP_ITEM_PAD = new Insets(4, 8, 4, 8);

    // Typography
    public static final Font LANG_FONT = new Font("SansSerif", Font.PLAIN, 13);

    // Flag box (renderer will preserve 3:2 aspect)
    public static final int FLAG_W = 24;
    public static final int FLAG_H = 16;

    // Spacing between icon and text
    public static final int LANG_ICON_TEXT_GAP = 8;
    public static final int LANG_ICON_CHEVRON_GAP = 6;
}

