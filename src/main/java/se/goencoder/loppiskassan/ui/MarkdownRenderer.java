package se.goencoder.loppiskassan.ui;

import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

/**
 * Converts Markdown text to HTML suitable for display in Swing {@code JEditorPane}.
 * Wraps the output in a minimal HTML document with styling that matches the app theme.
 */
public final class MarkdownRenderer {

    private static final Parser PARSER = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .escapeHtml(true)
            .build();

    private MarkdownRenderer() {}

    /**
     * Render markdown (or plain text) to an HTML string for use in a {@code JEditorPane}.
     * Always parses through CommonMark since plain text is valid markdown and
     * many events have markdown content without the flag set.
     *
     * @param text       the markdown or plain text
     * @param isMarkdown currently unused — kept for API compatibility
     * @return a complete HTML document string
     */
    public static String toHtml(String text, boolean isMarkdown) {
        if (text == null || text.isBlank()) {
            return wrapHtml("");
        }
        // Always parse through CommonMark — plain text is valid markdown
        // and many events have markdown content without the flag set.
        Node document = PARSER.parse(text);
        String body = RENDERER.render(document);
        return wrapHtml(body);
    }

    private static String wrapHtml(String body) {
        String textColor = hexColor(AppColors.TEXT_PRIMARY);
        String linkColor = hexColor(AppColors.ACCENT);
        return "<html><head><style>"
                + "body { font-family: SansSerif; font-size: 11pt; color: " + textColor + "; margin: 0; padding: 0; }"
                + "h1, h2, h3 { margin: 4px 0; }"
                + "p { margin: 2px 0; }"
                + "ul, ol { margin: 2px 0 2px 16px; padding: 0; }"
                + "a { color: " + linkColor + "; }"
                + "</style></head><body>" + body + "</body></html>";
    }

    private static String hexColor(java.awt.Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }
}
