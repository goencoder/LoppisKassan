package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import org.json.JSONObject;
import se.goencoder.loppiskassan.storage.LocalEvent;
import se.goencoder.loppiskassan.storage.LocalEventRepository;
import se.goencoder.loppiskassan.util.SwedishDateFormatter;
import se.goencoder.loppiskassan.config.ILoppisConfigurationStore;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

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
        String eventId = AppModeManager.getEventId();
        
        if (eventId == null || eventId.isEmpty()) {
            String placeholder = AppModeManager.isLocalMode()
                    ? "Lokalt evenemang"
                    : "iLoppis-evenemang";
            eventBadgeLabel.setText(placeholder);
            return;
        }
        
        // Hämta evenemangsnamn från event-data (lokal eller cached iLoppis-data)
        String eventName = getEventName(eventId);
        String eventDates = getEventDates(eventId);
        
        if (eventDates != null && !eventDates.isEmpty()) {
            eventBadgeLabel.setText(eventName + " • " + eventDates);
        } else {
            eventBadgeLabel.setText(eventName);
        }
    }
    
    private String getEventName(String eventId) {
        // Försök läsa riktigt namn från lagrad event-metadata (lokal)
        try {
            LocalEvent event = LocalEventRepository.load(eventId);
            if (event != null && event.getName() != null && !event.getName().isBlank()) {
                return event.getName();
            }
        } catch (IOException ignored) {
            // Fallback below
        }

        // Försök läsa namn från iLoppis-konfigurationen (cached eventData)
        if (AppModeManager.isILoppisMode()) {
            String eventData = ILoppisConfigurationStore.getEventData();
            if (eventData != null && !eventData.isBlank()) {
                try {
                    JSONObject obj = new JSONObject(eventData);
                    String name = obj.optString("name", "");
                    if (!name.isBlank()) {
                        return name;
                    }
                } catch (Exception ignored) {
                    // Fallback below
                }
            }
        }

        // Fallback till ID eller standardnamn
        if (eventId.equals("local-test")) {
            return "Lokalt evenemang";
        }
        return eventId;
    }
    
    private String getEventDates(String eventId) {
        // Try to read dates from iLoppis event data (online mode)
        if (AppModeManager.isILoppisMode()) {
            String eventData = ILoppisConfigurationStore.getEventData();
            if (eventData != null && !eventData.isBlank()) {
                try {
                    JSONObject obj = new JSONObject(eventData);
                    String startTimeStr = obj.optString("startTime", "");
                    String endTimeStr = obj.optString("endTime", "");
                    
                    if (!startTimeStr.isBlank() && !endTimeStr.isBlank()) {
                        return formatEventDateRange(startTimeStr, endTimeStr);
                    } else if (!startTimeStr.isBlank()) {
                        return formatSingleDate(startTimeStr);
                    }
                } catch (Exception ignored) {
                    // Fallback to no dates
                }
            }
        }
        
        // Local events don't have start/end dates, only createdAt
        return "";
    }
    
    private String formatEventDateRange(String startTimeStr, String endTimeStr) {
        try {
            java.time.OffsetDateTime startTime = java.time.OffsetDateTime.parse(startTimeStr);
            java.time.OffsetDateTime endTime = java.time.OffsetDateTime.parse(endTimeStr);
            
            java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM", 
                new java.util.Locale(LocalizationManager.getLanguage()));
            
            // If same day, show only one date
            if (startTime.toLocalDate().equals(endTime.toLocalDate())) {
                return startTime.format(dateFormatter);
            }
            
            // Different days - show range
            return startTime.format(dateFormatter) + " - " + endTime.format(dateFormatter);
        } catch (Exception e) {
            return "";
        }
    }
    
    private String formatSingleDate(String dateTimeStr) {
        try {
            java.time.OffsetDateTime dateTime = java.time.OffsetDateTime.parse(dateTimeStr);
            java.time.format.DateTimeFormatter dateFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM", 
                new java.util.Locale(LocalizationManager.getLanguage()));
            return dateTime.format(dateFormatter);
        } catch (Exception e) {
            return "";
        }
    }
    
    @Override
    public void reloadTexts() {
        updateEventBadge();
    }
}
