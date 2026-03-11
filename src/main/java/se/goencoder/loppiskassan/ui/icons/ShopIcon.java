package se.goencoder.loppiskassan.ui.icons;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.GeneralPath;

/**
 * Iconsax "shop" icon rendered via Java2D.
 * Source: <a href="https://iconsax-react.pages.dev/">iconsax-react</a> — shop (linear).
 * Matches the icon used in the iLoppis logo SVG.
 */
public final class ShopIcon implements Icon {
    private final int size;
    private final Color color;

    public ShopIcon(int size, Color color) {
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

        // Storefront body
        GeneralPath body = new GeneralPath();
        body.moveTo(3.01, 11.22);
        body.lineTo(3.01, 15.71);
        body.curveTo(3.01, 20.2, 4.81, 22, 9.3, 22);
        body.lineTo(14.69, 22);
        body.curveTo(19.18, 22, 20.98, 20.2, 20.98, 15.71);
        body.lineTo(20.98, 11.22);
        g2.draw(body);

        // Center awning
        GeneralPath center = new GeneralPath();
        center.moveTo(12, 12);
        center.curveTo(13.83, 12, 15.18, 10.51, 15, 8.68);
        center.lineTo(14.34, 2);
        center.lineTo(9.67, 2);
        center.lineTo(9, 8.68);
        center.curveTo(8.82, 10.51, 10.17, 12, 12, 12);
        center.closePath();
        g2.draw(center);

        // Right awning
        GeneralPath right = new GeneralPath();
        right.moveTo(18.31, 12);
        right.curveTo(20.33, 12, 21.81, 10.36, 21.61, 8.35);
        right.lineTo(21.33, 5.6);
        right.curveTo(20.97, 3, 19.97, 2, 17.35, 2);
        right.lineTo(14.3, 2);
        right.lineTo(15, 9.01);
        right.curveTo(15.17, 10.66, 16.66, 12, 18.31, 12);
        right.closePath();
        g2.draw(right);

        // Left awning
        GeneralPath left = new GeneralPath();
        left.moveTo(5.64, 12);
        left.curveTo(7.29, 12, 8.78, 10.66, 8.94, 9.01);
        left.lineTo(9.16, 6.8);
        left.lineTo(9.64, 2);
        left.lineTo(6.59, 2);
        left.curveTo(3.97, 2, 2.97, 3, 2.61, 5.6);
        left.lineTo(2.34, 8.35);
        left.curveTo(2.14, 10.36, 3.62, 12, 5.64, 12);
        left.closePath();
        g2.draw(left);

        // Door
        GeneralPath door = new GeneralPath();
        door.moveTo(12, 17);
        door.curveTo(10.33, 17, 9.5, 17.83, 9.5, 19.5);
        door.lineTo(9.5, 22);
        door.lineTo(14.5, 22);
        door.lineTo(14.5, 19.5);
        door.curveTo(14.5, 17.83, 13.67, 17, 12, 17);
        door.closePath();
        g2.draw(door);

        g2.dispose();
    }
}
