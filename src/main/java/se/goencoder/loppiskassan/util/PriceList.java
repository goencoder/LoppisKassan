package se.goencoder.loppiskassan.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility for parsing whitespace separated integer price tokens.
 */
public final class PriceList {
    private static final Pattern TOKEN = Pattern.compile("\\d+");
    private PriceList() {}

    /**
     * Parse a string of numbers separated by whitespace into a list of integers.
     *
     * @param s input string, may not be null or blank
     * @return list of parsed integers
     * @throws NumberFormatException if the string is null, blank or contains non-digit tokens
     */
    public static List<Integer> parse(String s) throws NumberFormatException {
        if (s == null || s.isBlank()) throw new NumberFormatException("empty");
        List<Integer> out = new ArrayList<>();
        for (String t : s.trim().split("\\s+")) {
            if (!TOKEN.matcher(t).matches()) throw new NumberFormatException(t);
            out.add(Integer.parseInt(t));
        }
        return out;
    }
}
