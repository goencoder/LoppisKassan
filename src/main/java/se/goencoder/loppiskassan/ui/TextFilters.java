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
        private String normalize(String s) {
            // allow digits and spaces, collapse multiple spaces, trim edges
            s = s.replaceAll("[^0-9\\s]", "");
            s = s.trim().replaceAll("\\s+", " ");
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
}

