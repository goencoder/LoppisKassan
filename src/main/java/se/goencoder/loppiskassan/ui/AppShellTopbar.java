package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.util.SwedishDateFormatter;

import javax.swing.*;
import java.awt.*;

/**
 * Topbar för App Shell.
 * Visar appnamn (vänster), aktivt evenemang (mitten), och språkväljare (höger).
 */
public class AppShellTopbar extends JPanel implements LocalizationAware {
    
    private final JLabel appNameLabel;
    private final JLabel eventBadgeLabel;
    private final LanguageSelector languageSelector;
    
    public AppShellTopbar() {
        setLayout(new BorderLayout());
        setBackground(AppColors.WHITE);
        setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, AppColors.BORDER),
            BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));
        
        // Vänster: Appnamn
        appNameLabel = new JLabel("iLoppis Kassa");
        appNameLabel.setFont(appNameLabel.getFont().deriveFont(Font.BOLD, 16f));
        appNameLabel.setForeground(AppColors.TEXT_PRIMARY);
        
        // Mitten: Evenemangsbadge
        eventBadgeLabel = new JLabel();
        eventBadgeLabel.setFont(eventBadgeLabel.getFont().deriveFont(Font.PLAIN, 14f));
        eventBadgeLabel.setForeground(AppColors.TEXT_PRIMARY);
        eventBadgeLabel.setHorizontalAlignment(SwingConstants.CENTER);
        updateEventBadge();
        
        // Höger: Språkväljare
        languageSelector = new LanguageSelector();
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.setOpaque(false);
        rightPanel.add(languageSelector);
        
        add(appNameLabel, BorderLayout.WEST);
        add(eventBadgeLabel, BorderLayout.CENTER);
        add(rightPanel, BorderLayout.EAST);
    }
    
    private void updateEventBadge() {
        String eventId = ConfigurationStore.EVENT_ID_STR.get();
        
        if (eventId == null || eventId.isEmpty()) {
            eventBadgeLabel.setText("");
            return;
        }
        
        // Hämta evenemangsnamn från config (TODO: läs från event-data när tillgängligt)
        String eventName = getEventName(eventId);
        String eventDates = getEventDates(eventId);
        
        if (eventDates != null && !eventDates.isEmpty()) {
            eventBadgeLabel.setText(eventName + " • " + eventDates);
        } else {
            eventBadgeLabel.setText(eventName);
        }
    }
    
    private String getEventName(String eventId) {
        // Tillfällig lösning: använd event ID som namn
        // TODO: Läs riktigt namn från event-objekt/state
        return eventId.equals("local-test") ? "Lokalt evenemang" : eventId;
    }
    
    private String getEventDates(String eventId) {
        // TODO: Läs riktiga datum från event-objekt/state
        // För tillfället returnerar vi inget datum
        return "";
    }
    
    @Override
    public void reloadTexts() {
        updateEventBadge();
    }
}
