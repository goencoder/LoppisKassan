package se.goencoder.loppiskassan.ui;

import javax.swing.JTextField;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

/**
 * Small helpers to constrain text fields without jumpy key listeners.
 * Keeps caret stable and prevents invalid characters at the source.
 */
public final class TextFilters {
    private TextFilters() {}

    /** Install a DocumentFilter on a JTextField. */
    public static void install(JTextField field, DocumentFilter filter) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(filter);
    }

    /** Digits only, with optional max length. */
    public static final class DigitsOnlyFilter extends DocumentFilter {
        private final int maxLen; // <= 0 means unlimited
        public DigitsOnlyFilter(int maxLen) { this.maxLen = maxLen; }
        @Override public void insertString(FilterBypass fb, int off, String str, AttributeSet a) throws BadLocationException {
            if (str == null) return;
            String s = str.replaceAll("[^0-9]", "");
            if (s.isEmpty()) return;
            if (maxLen > 0 && fb.getDocument().getLength() + s.length() > maxLen) {
                int allowed = maxLen - fb.getDocument().getLength();
                if (allowed <= 0) return;
                s = s.substring(0, allowed);
            }
            super.insertString(fb, off, s, a);
        }
        @Override public void replace(FilterBypass fb, int off, int len, String str, AttributeSet a) throws BadLocationException {
            if (str == null) { super.replace(fb, off, len, str, a); return; }
            String before = fb.getDocument().getText(0, fb.getDocument().getLength());
            String after  = before.substring(0, off) + str + before.substring(off + len);
            after = after.replaceAll("[^0-9]", "");
            if (maxLen > 0 && after.length() > maxLen) {
                after = after.substring(0, maxLen);
            }
            super.replace(fb, 0, fb.getDocument().getLength(), after, a);
        }
    }

    /** Digits and single spaces; collapses multiple spaces; trims. */
    public static final class DigitsAndSpacesFilter extends DocumentFilter {
        private final int maxLen; // <= 0 means unlimited
        public DigitsAndSpacesFilter(int maxLen) { this.maxLen = maxLen; }
        /**
         * Allow digits and spaces. Collapse runs of 2+ spaces to a single space,
         * but DO NOT trim—so a user-typed trailing space remains visible
         * (important when entering "12 23 34 ...").
         */
        private String normalize(String s) {
            // remove all characters except digits and spaces
            s = s.replaceAll("[^0-9\\s]", "");
            // collapse multiple spaces anywhere, but keep a trailing single space if present
            // (no trim here by design)
            s = s.replaceAll(" {2,}", " ");
            return s;
        }
        @Override public void insertString(FilterBypass fb, int off, String str, AttributeSet a) throws BadLocationException {
            if (str == null) return;
            String before = fb.getDocument().getText(0, fb.getDocument().getLength());
            String after  = normalize(before.substring(0, off) + str + before.substring(off));
            if (maxLen > 0 && after.length() > maxLen) after = after.substring(0, maxLen);
            super.replace(fb, 0, fb.getDocument().getLength(), after, a);
        }
        @Override public void replace(FilterBypass fb, int off, int len, String str, AttributeSet a) throws BadLocationException {
            String before = fb.getDocument().getText(0, fb.getDocument().getLength());
            String after  = normalize(before.substring(0, off) + (str == null ? "" : str) + before.substring(off + len));
            if (maxLen > 0 && after.length() > maxLen) after = after.substring(0, maxLen);
            super.replace(fb, 0, fb.getDocument().getLength(), after, a);
        }
    }

    /**
     * Uppercase alpha-numeric with optional dashes ("-").
     * Useful for codes like "B6I-DKU". Converts to UPPERCASE automatically.
     */
    public static final class AlnumDashUpperFilter extends DocumentFilter {
        private final int maxLen; // <= 0 means unlimited
        public AlnumDashUpperFilter(int maxLen) { this.maxLen = maxLen; }
        private String normalize(String s) {
            if (s == null) return "";
            // keep A–Z, a–z, 0–9 and '-'; drop the rest; then uppercase
            String cleaned = s.replaceAll("[^A-Za-z0-9-]", "");
            return cleaned.toUpperCase(java.util.Locale.ROOT);
        }
        @Override public void insertString(FilterBypass fb, int off, String str, AttributeSet a) throws BadLocationException {
            if (str == null) return;
            String before = fb.getDocument().getText(0, fb.getDocument().getLength());
            String after  = before.substring(0, off) + normalize(str) + before.substring(off);
            if (maxLen > 0 && after.length() > maxLen) after = after.substring(0, maxLen);
            super.replace(fb, 0, fb.getDocument().getLength(), after, a);
        }
        @Override public void replace(FilterBypass fb, int off, int len, String str, AttributeSet a) throws BadLocationException {
            String before = fb.getDocument().getText(0, fb.getDocument().getLength());
            String repl   = (str == null ? "" : normalize(str));
            String after  = before.substring(0, off) + repl + before.substring(off + len);
            if (maxLen > 0 && after.length() > maxLen) after = after.substring(0, maxLen);
            super.replace(fb, 0, fb.getDocument().getLength(), after, a);
        }
    }
}
