package se.goencoder.loppiskassan.ui;

import javax.swing.*;
import java.awt.*;

/**
 * Snackbar notification panel with auto-dismiss.
 * Shown at bottom of parent container for 4 seconds.
 * 
 * Usage:
 * <pre>
 * SnackbarPanel snackbar = new SnackbarPanel();
 * // Add to container at bottom
 * snackbar.show("✔ Köp registrerat (Swish, 382 kr)");
 * </pre>
 */
public class SnackbarPanel extends JPanel {
    private final JLabel messageLabel;
    private Timer dismissTimer;
    private boolean isHiding = false; // Prevent infinite recursion
    private boolean hasMessage = false;
    
    public SnackbarPanel() {
        setLayout(new BorderLayout(12, 0));
        setBackground(AppColors.TEXT_PRIMARY); // Dark background
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        setVisible(false);
        
        messageLabel = new JLabel();
        messageLabel.setForeground(AppColors.WHITE);
        messageLabel.setFont(messageLabel.getFont().deriveFont(13f));
        
        add(messageLabel, BorderLayout.CENTER);
    }
    
    /**
     * Show snackbar with message.
     * Auto-dismisses after 4 seconds.
     * 
     * @param message Message to display (supports HTML)
     */
    public void show(String message) {
        messageLabel.setText(message);
        hasMessage = true;
        setVisible(true);
        
        // Restart timer
        if (dismissTimer != null) {
            dismissTimer.stop();
        }
        dismissTimer = new Timer(4000, e -> hide());
        dismissTimer.setRepeats(false);
        dismissTimer.start();
        
        // Request repaint to ensure visibility
        revalidate();
        repaint();
    }
    
    /**
     * Hide the snackbar immediately.
     */
    public void hide() {
        if (isHiding) return; // Prevent recursive calls
        
        isHiding = true;
        try {
            if (dismissTimer != null) {
                dismissTimer.stop();
            }
            hasMessage = false;
            setVisible(false);
            revalidate();
            repaint();
        } finally {
            isHiding = false;
        }
    }

    @Override
    public Dimension getPreferredSize() {
        // Collapse height entirely when hidden or when there is no message, so no dark line is shown
        if (!hasMessage) {
            return new Dimension(0, 0);
        }
        return super.getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
        if (!hasMessage) {
            return new Dimension(0, 0);
        }
        return super.getMaximumSize();
    }

    @Override
    public Dimension getMinimumSize() {
        if (!hasMessage) {
            return new Dimension(0, 0);
        }
        return super.getMinimumSize();
    }
}
