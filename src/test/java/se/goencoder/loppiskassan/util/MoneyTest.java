package se.goencoder.loppiskassan.util;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class MoneyTest {
    @Test
    void formatAmountSimple() {
        String s = Money.formatAmount(205, Locale.US, "SEK");
        assertEquals("205 SEK", s);
    }

    @Test
    void formatAmountGrouping() {
        String s = Money.formatAmount(12345, Locale.US, "SEK");
        assertEquals("12,345 SEK", s);
    }
}
