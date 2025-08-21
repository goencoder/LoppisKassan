package se.goencoder.loppiskassan.ui;

import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

/**
 * Common table cell renderers.
 */
public final class Renderers {
    private Renderers() {}

    /**
     * Renderer that right aligns cell contents.
     *
     * @return table cell renderer with right alignment
     */
    public static TableCellRenderer rightAligned() {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer();
        r.setHorizontalAlignment(SwingConstants.RIGHT);
        return r;
    }
}
