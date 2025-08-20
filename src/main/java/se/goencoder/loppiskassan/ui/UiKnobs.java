package se.goencoder.loppiskassan.ui;

/**
 * Centralt ställe för UI-inställningar/"kontrollknoppar".
 * Justera här utan att behöva gräva i komponentkoden.
 */
public final class UiKnobs {
    private UiKnobs() {}

    // Språkväljaren - Display options
    public static int LANG_ICON_SIZE_PX = 24;          // Size of PNG flag icons
    public static boolean LANG_BUTTON_SHOW_TEXT = false; // Show text in closed combobox (false for clean "flag + caret" look)
    public static boolean LANG_POPUP_SHOW_TEXT = true;   // Show language text in popup list
    public static String LANG_FLAGS_PATH = "/flags/"; // Path to PNG flag icons
}
