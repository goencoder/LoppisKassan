package se.goencoder.loppiskassan.utils;

import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Utility class for generating ULID (Universally Unique Lexicographically Sortable Identifier) strings.
 * Format: ^[0-9A-HJKMNP-TV-Z]{26}$
 *
 * ULIDs are 26 characters, consisting of:
 * - 10 characters of timestamp (milliseconds since epoch)
 * - 16 characters of randomness
 */
public class UlidGenerator {
    // ULID uses Crockford's base32 (excludes I, L, O, U)
    private static final char[] ENCODING_CHARS = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K',
            'M', 'N', 'P', 'Q', 'R', 'S', 'T', 'V', 'W', 'X',
            'Y', 'Z'
    };

    /**
     * Generate a ULID string that matches the regex pattern ^[0-9A-HJKMNP-TV-Z]{26}$
     * @return A 26-character ULID string
     */
    public static String generate() {
        return generate(System.currentTimeMillis());
    }

    /**
     * Generate a ULID with a specific timestamp
     * @param timestamp Milliseconds since epoch
     * @return A 26-character ULID string
     */
    public static String generate(long timestamp) {
        StringBuilder result = new StringBuilder(26);

        // Append 10 characters for timestamp (base32 encoded)
        appendBase32(result, timestamp, 10);

        // Append 16 random characters
        for (int i = 0; i < 16; i++) {
            int random = ThreadLocalRandom.current().nextInt(32);
            result.append(ENCODING_CHARS[random]);
        }

        return result.toString();
    }

    private static void appendBase32(StringBuilder builder, long value, int count) {
        // Loop backwards to encode timestamp in big-endian order (most significant bits first)
        for (int i = count - 1; i >= 0; i--) {
            int index = (int) ((value >> (i * 5)) & 0x1f); // 5 bits per character
            builder.append(ENCODING_CHARS[index]);
        }
    }
}
