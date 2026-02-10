package se.goencoder.loppiskassan.ui.components;

import se.goencoder.loppiskassan.ui.AppColors;
import javax.swing.*;
import java.awt.*;

/**
 * Snackbar notification with optional undo callback.
 * Shows at bottom of window, auto-dismisses after 4 seconds.
 */
public class SnackbarPanel extends JPanel {
    private final JLabel messageLabel;
    private final JButton undoButton;
    private Timer dismissTimer;
    private Runnable undoCallback;

    public SnackbarPanel() {
        setLayout(new BorderLayout(8, 0));
        setBackground(AppColors.TEXT_PRIMARY);
        setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));
        setVisible(false);

        messageLabel = new JLabel();
        messageLabel.setForeground(AppColors.WHITE);
        messageLabel.setFont(messageLabel.getFont().deriveFont(Font.PLAIN, 14f));

        undoButton = new JButton("Ångra");
        undoButton.setForeground(AppColors.ACCENT);
        undoButton.setBackground(AppColors.TEXT_PRIMARY);
        undoButton.setBorderPainted(false);
        undoButton.setFocusPainted(false);
        undoButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        undoButton.addActionListener(e -> {
            if (undoCallback != null) undoCallback.run();
            hide();
        });

        add(messageLabel, BorderLayout.CENTER);
        add(undoButton, BorderLayout.EAST);
    }

    /**
     * Shows a snackbar with a message and optional undo callback.
     * @param message The message to display
     * @param undoCallback Optional callback to run when "Ångra" is clicked (null to hide undo button)
     */
    public void show(String message, Runnable undoCallback) {
        this.undoCallback = undoCallback;
        messageLabel.setText(message);
        undoButton.setVisible(undoCallback != null);
        setVisible(true);

        if (dismissTimer != null) dismissTimer.stop();
        dismissTimer = new Timer(4000, e -> hide());
        dismissTimer.setRepeats(false);
        dismissTimer.start();
    }

    /**
     * Hides the snackbar immediately.
     */
    public void hide() {
        setVisible(false);
        if (dismissTimer != null) dismissTimer.stop();
    }
}
