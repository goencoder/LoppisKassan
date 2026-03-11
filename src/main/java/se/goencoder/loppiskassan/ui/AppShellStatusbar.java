package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.util.SwedishDateFormatter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDateTime;

/**
 * Statusrad för App Shell.
 * Visar lägesinformation (Lokal kassa / Ansluten till iLoppis) och senaste åtgärd/synk.
 */
public class AppShellStatusbar extends JPanel implements LocalizationAware {
    
    private final JLabel statusLabel;
    private final JLabel rejectedLabel;
    private final JLabel timestampLabel;
    private int pendingCount;
    private int rejectedCount;
    private Runnable pendingClickListener;
    private Runnable rejectedClickListener;
    
    public AppShellStatusbar() {
        setLayout(new BorderLayout());
        setBackground(AppColors.SURFACE);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, AppColors.BORDER),
            BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));
        
        // Vänster: Status (läge) + indikatorer
        JPanel statusPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        statusPanel.setBackground(AppColors.SURFACE);

        statusLabel = createStatusChip();
        statusLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (pendingCount > 0 && pendingClickListener != null) {
                    pendingClickListener.run();
                }
            }
        });

        rejectedLabel = createStatusChip();
        rejectedLabel.setVisible(false);
        rejectedLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (rejectedCount > 0 && rejectedClickListener != null) {
                    rejectedClickListener.run();
                }
            }
        });

        statusPanel.add(statusLabel);
        statusPanel.add(rejectedLabel);
        updateStatus();
        
        // Höger: Tidstämpel
        timestampLabel = new JLabel();
        timestampLabel.setFont(timestampLabel.getFont().deriveFont(Font.PLAIN, 12f));
        timestampLabel.setForeground(AppColors.TEXT_MUTED);
        
        add(statusPanel, BorderLayout.WEST);
        add(timestampLabel, BorderLayout.EAST);
    }
    
    private void updateStatus() {
        if (AppModeManager.isLocalMode()) {
            setStatusChip(statusLabel, AppColors.SUCCESS, LocalizationManager.tr("status.local_mode"));
            statusLabel.setCursor(Cursor.getDefaultCursor());
        } else {
            if (pendingCount > 0) {
                setStatusChip(statusLabel, AppColors.WARNING,
                        LocalizationManager.tr("status.offline_pending", pendingCount));
                statusLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            } else {
                setStatusChip(statusLabel, AppColors.SUCCESS, LocalizationManager.tr("status.online_mode"));
                statusLabel.setCursor(Cursor.getDefaultCursor());
            }
        }
    }
    
    /**
     * Uppdaterar tidstämpeln för senaste åtgärd.
     */
    public void setLastAction(String action) {
        String time = SwedishDateFormatter.formatTime(LocalDateTime.now());
        timestampLabel.setText(action + ": " + time);
    }
    
    /**
     * Visar offline-status med antal väntande poster (för iLoppis-läge).
     */
    public void setOfflineStatus(int pendingCount) {
        setPendingStatus(pendingCount);
    }

    public void setPendingStatus(int pendingCount) {
        this.pendingCount = pendingCount;
        updateStatus();
    }

    public void setRejectedStatus(int rejectedCount) {
        this.rejectedCount = rejectedCount;
        if (rejectedCount > 0 && !AppModeManager.isLocalMode()) {
            setStatusChip(rejectedLabel, AppColors.DANGER,
                    LocalizationManager.tr("status.rejected_items", rejectedCount));
            rejectedLabel.setVisible(true);
            rejectedLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            rejectedLabel.setVisible(false);
            rejectedLabel.setCursor(Cursor.getDefaultCursor());
        }
    }
    
    /**
     * Återställer till online-status.
     */
    public void setOnlineStatus() {
        this.pendingCount = 0;
        updateStatus();
    }

    public void setPendingClickListener(Runnable listener) {
        this.pendingClickListener = listener;
    }

    public void setRejectedClickListener(Runnable listener) {
        this.rejectedClickListener = listener;
    }
    
    @Override
    public void reloadTexts() {
        updateStatus();
        setRejectedStatus(rejectedCount);
    }

    private JLabel createStatusChip() {
        JLabel label = new JLabel();
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
        label.setForeground(AppColors.TEXT_PRIMARY);
        label.setOpaque(true);
        label.setBackground(AppColors.FIELD_BG);
        label.setBorder(new RoundedBorder(AppColors.BORDER, 1, 14, new Insets(4, 10, 4, 10)));
        label.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return label;
    }

    private void setStatusChip(JLabel label, Color dotColor, String text) {
        label.setText(text);
        label.setIcon(new DotIcon(dotColor, 8));
        label.setIconTextGap(6);
    }

    private static class DotIcon implements Icon {
        private final Color color;
        private final int size;

        private DotIcon(Color color, int size) {
            this.color = color;
            this.size = size;
        }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.fillOval(x, y + (getIconHeight() - size) / 2, size, size);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
