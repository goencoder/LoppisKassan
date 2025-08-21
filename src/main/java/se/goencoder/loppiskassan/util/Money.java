package se.goencoder.loppiskassan.util;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Utility for formatting money amounts without decimals, using locale grouping.
 */
public final class Money {
    private Money() {}

    /**
     * Format a money amount without decimals using locale aware grouping.
     *
     * @param amount amount in whole currency units
     * @param locale locale for formatting
     * @param currencyLabel localized currency label (e.g., SEK)
     * @return formatted string, e.g., "1,234 SEK"
     */
    public static String formatAmount(int amount, Locale locale, String currencyLabel) {
        NumberFormat nf = NumberFormat.getIntegerInstance(locale); // no decimals
        nf.setGroupingUsed(true);
        return nf.format(amount) + " " + currencyLabel;
    }
}
