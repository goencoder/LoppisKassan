package se.goencoder.loppiskassan.ui.icons;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

public final class FlagIcon implements Icon {
    private final Image multiResImage;
    private final int targetW;
    private final int targetH;

    public FlagIcon(String resourceBaseName, int targetW, int targetH) {
        this.targetW = targetW;
        this.targetH = targetH;

        BufferedImage img1x = load(resourceBaseName + "@1x.png");
        BufferedImage img2x = load(resourceBaseName + "@2x.png");

        this.multiResImage = new BaseMultiResolutionImage(img1x, img2x);
    }

    private static BufferedImage load(String path) {
        try (InputStream is = FlagIcon.class.getClassLoader().getResourceAsStream(path)) {
            if (is == null) throw new IllegalArgumentException("Missing resource: " + path);
            return ImageIO.read(is);
        } catch (IOException e) {
            throw new RuntimeException("Cannot read resource: " + path, e);
        }
    }

    @Override public int getIconWidth() { return targetW; }
    @Override public int getIconHeight() { return targetH; }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int boxW = targetW, boxH = targetH;
        int srcW = multiResImage.getWidth(null), srcH = multiResImage.getHeight(null);
        double scale = Math.min((double) boxW / srcW, (double) boxH / srcH);
        int drawW = (int) Math.round(srcW * scale);
        int drawH = (int) Math.round(srcH * scale);
        int dx = x + (boxW - drawW) / 2;
        int dy = y + (boxH - drawH) / 2;

        g2.drawImage(multiResImage, dx, dy, drawW, drawH, null);
        g2.dispose();
    }
}

