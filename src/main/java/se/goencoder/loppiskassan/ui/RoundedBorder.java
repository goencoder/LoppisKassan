package se.goencoder.loppiskassan.ui;

import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * A border that draws a rounded rectangle with optional fill and stroke.
 * Used by AppButton to provide consistent rounded corners across all buttons.
 */
public class RoundedBorder extends AbstractBorder {
    private final Color color;
    private final int thickness;
    private final int radius;
    private final boolean filled;
    private final Insets insets;

    /**
     * Create a rounded border with stroke only.
     */
    public RoundedBorder(Color color, int thickness, int radius, Insets insets) {
        this(color, thickness, radius, insets, false);
    }

    /**
     * Create a rounded border with optional fill.
     */
    public RoundedBorder(Color color, int thickness, int radius, Insets insets, boolean filled) {
        this.color = color;
        this.thickness = thickness;
        this.radius = radius;
        this.insets = insets;
        this.filled = filled;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        
        if (filled) {
            g2.setColor(color);
            g2.fill(new RoundRectangle2D.Float(x, y, width - 1, height - 1, radius, radius));
        }
        
        if (thickness > 0) {
            g2.setColor(color);
            g2.setStroke(new BasicStroke(thickness));
            g2.draw(new RoundRectangle2D.Float(
                x + thickness / 2f,
                y + thickness / 2f,
                width - thickness - 1,
                height - thickness - 1,
                radius,
                radius
            ));
        }
        
        g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return insets;
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.left = this.insets.left;
        insets.top = this.insets.top;
        insets.right = this.insets.right;
        insets.bottom = this.insets.bottom;
        return insets;
    }
}
