package se.goencoder.loppiskassan.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PriceListTest {
    @Test
    void parseValid() {
        List<Integer> list = PriceList.parse("10 20 30");
        assertEquals(List.of(10, 20, 30), list);
    }

    @Test
    void parseZeroIsValid() {
        List<Integer> list = PriceList.parse("10 0 20");
        assertEquals(List.of(10, 0, 20), list);
    }

    @Test
    void parseInvalid() {
        assertThrows(NumberFormatException.class, () -> PriceList.parse("10a 20"));
    }

    @Test
    void parseNegativeIsInvalid() {
        assertThrows(NumberFormatException.class, () -> PriceList.parse("10 -1 20"));
    }

    @Test
    void parseSpaces() {
        List<Integer> list = PriceList.parse("  10  20   30 ");
        assertEquals(List.of(10, 20, 30), list);
    }
}
