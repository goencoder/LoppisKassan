package se.goencoder.loppiskassan.ui.icons;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.GeneralPath;

/**
 * Iconsax "monitor" icon rendered via Java2D.
 * Source: <a href="https://iconsax-react.pages.dev/">iconsax-react</a> — monitor (linear).
 * Used for the "Local" mode button on the splash screen.
 */
public final class MonitorIcon implements Icon {
    private final int size;
    private final Color color;

    public MonitorIcon(int size, Color color) {
        this.size = size;
        this.color = color;
    }

    @Override public int getIconWidth()  { return size; }
    @Override public int getIconHeight() { return size; }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double scale = size / 24.0;
        g2.translate(x, y);
        g2.scale(scale, scale);

        g2.setColor(color);
        g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        // Monitor body (rounded rect)
        GeneralPath body = new GeneralPath();
        body.moveTo(6.44, 2);
        body.lineTo(17.55, 2);
        body.curveTo(21.11, 2, 22, 2.89, 22, 6.44);
        body.lineTo(22, 13.55);
        body.curveTo(22, 17.11, 21.11, 18, 17.56, 18);
        body.lineTo(6.44, 18);
        body.curveTo(2.89, 18, 2, 17.11, 2, 13.56);
        body.lineTo(2, 6.44);
        body.curveTo(2, 2.89, 2.89, 2, 6.44, 2);
        body.closePath();
        g2.draw(body);

        // Stand line
        g2.drawLine(12, 18, 12, 22);

        // Base
        g2.drawLine(8, 22, 16, 22);

        g2.dispose();
    }
}
