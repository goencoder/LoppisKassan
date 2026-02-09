package se.goencoder.loppiskassan.util;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Utility för svensk datumformatering enligt designdokument 003.
 * 
 * Formatregler:
 * - Evenemangsperiod: "8 feb – 9 feb 2026"
 * - Tid inom evenemang: "10:12"
 * - Tid med datum: "8 feb 10:12"
 */
public class SwedishDateFormatter {
    
    private static final Locale SWEDISH = new Locale("sv", "SE");
    
    // "8 feb – 9 feb 2026"
    private static final DateTimeFormatter EVENT_PERIOD = 
        DateTimeFormatter.ofPattern("d MMM – d MMM yyyy", SWEDISH);
    
    // "10:12"
    private static final DateTimeFormatter TIME_ONLY = 
        DateTimeFormatter.ofPattern("HH:mm", SWEDISH);
    
    // "8 feb 10:12"
    private static final DateTimeFormatter DATE_WITH_TIME = 
        DateTimeFormatter.ofPattern("d MMM HH:mm", SWEDISH);
    
    // "8 feb"
    private static final DateTimeFormatter SHORT_DATE = 
        DateTimeFormatter.ofPattern("d MMM", SWEDISH);
    
    /**
     * Formaterar en tidstämpel som "10:12" (endast tid).
     */
    public static String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(TIME_ONLY);
    }
    
    /**
     * Formaterar en tidstämpel som "8 feb 10:12" (datum + tid).
     */
    public static String formatDateWithTime(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(DATE_WITH_TIME);
    }
    
    /**
     * Formaterar en tidstämpel som "8 feb 10:12" (datum + tid) från ZonedDateTime.
     */
    public static String formatDateWithTime(ZonedDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(DATE_WITH_TIME);
    }
    
    /**
     * Formaterar kort datum som "8 feb".
     */
    public static String formatShortDate(LocalDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(SHORT_DATE);
    }
    
    /**
     * Formaterar kort datum som "8 feb" från ZonedDateTime.
     */
    public static String formatShortDate(ZonedDateTime dateTime) {
        if (dateTime == null) return "";
        return dateTime.format(SHORT_DATE);
    }
}
