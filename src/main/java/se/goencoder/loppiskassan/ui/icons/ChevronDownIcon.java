package se.goencoder.loppiskassan.ui.icons;

import javax.swing.*;
import java.awt.*;

public final class ChevronDownIcon implements Icon {
    private final int w, h;

    public ChevronDownIcon(int w, int h) { this.w = w; this.h = h; }

    @Override public int getIconWidth() { return w; }
    @Override public int getIconHeight() { return h; }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setStroke(new BasicStroke(Math.max(1f, w / 10f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int pad = Math.max(1, w / 6);
        int x1 = x + pad, x2 = x + w - pad;
        int cy = y + h / 2;
        int mid = (x1 + x2) / 2;

        g2.setColor(new Color(60, 60, 60));
        g2.drawLine(x1, cy - 2, mid, cy + 2);
        g2.drawLine(mid, cy + 2, x2, cy - 2);
        g2.dispose();
    }
}
