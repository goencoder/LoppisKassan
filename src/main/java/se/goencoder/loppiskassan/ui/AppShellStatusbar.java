package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.util.SwedishDateFormatter;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;

/**
 * Statusrad för App Shell.
 * Visar lägesinformation (Lokal kassa / Ansluten till iLoppis) och senaste åtgärd/synk.
 */
public class AppShellStatusbar extends JPanel implements LocalizationAware {
    
    private final JLabel statusLabel;
    private final JLabel timestampLabel;
    
    public AppShellStatusbar() {
        setLayout(new BorderLayout());
        setBackground(new Color(0xF7FAFC));
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xE2E8F0)),
            BorderFactory.createEmptyBorder(8, 16, 8, 16)
        ));
        
        // Vänster: Status (läge)
        statusLabel = new JLabel();
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 12f));
        updateStatus();
        
        // Höger: Tidstämpel
        timestampLabel = new JLabel();
        timestampLabel.setFont(timestampLabel.getFont().deriveFont(Font.PLAIN, 12f));
        timestampLabel.setForeground(new Color(0x718096));
        
        add(statusLabel, BorderLayout.WEST);
        add(timestampLabel, BorderLayout.EAST);
    }
    
    private void updateStatus() {
        if (AppModeManager.isLocalMode()) {
            statusLabel.setText("🟢 " + LocalizationManager.tr("status.local_mode"));
        } else {
            statusLabel.setText("🟢 " + LocalizationManager.tr("status.online_mode"));
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
        if (!AppModeManager.isLocalMode()) {
            statusLabel.setText("🟡 " + LocalizationManager.tr("status.offline_pending", pendingCount));
        }
    }
    
    /**
     * Återställer till online-status.
     */
    public void setOnlineStatus() {
        updateStatus();
    }
    
    @Override
    public void reloadTexts() {
        updateStatus();
    }
}
