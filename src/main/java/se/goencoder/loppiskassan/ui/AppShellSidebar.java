package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.localization.LocalizationManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Sidebar för App Shell navigation.
 * Visar navigationsknappar för Kassa, Historik, Export/Import, Arkiv.
 */
public class AppShellSidebar extends JPanel implements LocalizationAware {
    
    private final Consumer<AppShellFrame.NavigationTarget> onNavigate;
    private final List<NavigationButton> buttons = new ArrayList<>();
    
    private NavigationButton eventButton;
    private NavigationButton cashierButton;
    private NavigationButton historyButton;
    private NavigationButton exportButton;
    private NavigationButton archiveButton;
    
    public AppShellSidebar(Consumer<AppShellFrame.NavigationTarget> onNavigate) {
        this.onNavigate = onNavigate;
        
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(new Color(0xF7FAFC));
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, new Color(0xE2E8F0)));
        setPreferredSize(new Dimension(180, 0));
        
        add(Box.createVerticalStrut(8));
        
        // Evenemang (alltid synlig – hantera/skapa/byta evenemang)
        eventButton = createNavigationButton("sidebar.event", AppShellFrame.NavigationTarget.DISCOVERY);
        buttons.add(eventButton);
        add(eventButton);
        add(Box.createVerticalStrut(4));
        
        // Kassa (alltid synlig)
        cashierButton = createNavigationButton("sidebar.cashier", AppShellFrame.NavigationTarget.CASHIER);
        buttons.add(cashierButton);
        add(cashierButton);
        add(Box.createVerticalStrut(4));
        
        // Historik (alltid synlig)
        historyButton = createNavigationButton("sidebar.history", AppShellFrame.NavigationTarget.HISTORY);
        buttons.add(historyButton);
        add(historyButton);
        add(Box.createVerticalStrut(4));
        
        // Export/Import (endast lokal kassa)
        if (AppModeManager.isLocalMode()) {
            exportButton = createNavigationButton("sidebar.export", AppShellFrame.NavigationTarget.EXPORT);
            buttons.add(exportButton);
            add(exportButton);
            add(Box.createVerticalStrut(4));
            
            // Arkiv (endast lokal kassa)
            archiveButton = createNavigationButton("sidebar.archive", AppShellFrame.NavigationTarget.ARCHIVE);
            buttons.add(archiveButton);
            add(archiveButton);
            add(Box.createVerticalStrut(4));
        }
        
        add(Box.createVerticalGlue());
        
        // Välj första knappen som markerad
        if (!buttons.isEmpty()) {
            buttons.get(0).setSelected(true);
        }
    }
    
    private NavigationButton createNavigationButton(String textKey, AppShellFrame.NavigationTarget target) {
        NavigationButton button = new NavigationButton(LocalizationManager.tr(textKey));
        button.addActionListener(e -> {
            onNavigate.accept(target);
        });
        return button;
    }
    
    /**
     * Markerar angiven navigationsmål som vald.
     */
    public void setSelected(AppShellFrame.NavigationTarget target) {
        buttons.forEach(btn -> btn.setSelected(false));
        
        NavigationButton targetButton = switch (target) {
            case DISCOVERY -> eventButton;
            case CASHIER -> cashierButton;
            case HISTORY -> historyButton;
            case EXPORT -> exportButton;
            case ARCHIVE -> archiveButton;
        };
        
        if (targetButton != null) {
            targetButton.setSelected(true);
        }
    }
    
    @Override
    public void reloadTexts() {
        if (eventButton != null) eventButton.setText(LocalizationManager.tr("sidebar.event"));
        if (cashierButton != null) cashierButton.setText(LocalizationManager.tr("sidebar.cashier"));
        if (historyButton != null) historyButton.setText(LocalizationManager.tr("sidebar.history"));
        if (exportButton != null) exportButton.setText(LocalizationManager.tr("sidebar.export"));
        if (archiveButton != null) archiveButton.setText(LocalizationManager.tr("sidebar.archive"));
    }
    
    /**
     * Custom knapp för sidebar-navigering.
     */
    private static class NavigationButton extends JButton {
        private boolean selected = false;
        
        public NavigationButton(String text) {
            super(text);
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setHorizontalAlignment(SwingConstants.LEFT);
            setFont(getFont().deriveFont(Font.PLAIN, 13f));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
            setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setOpaque(false);
            
            updateAppearance();
        }
        
        public void setSelected(boolean selected) {
            this.selected = selected;
            updateAppearance();
            repaint();
        }
        
        private void updateAppearance() {
            if (selected) {
                setForeground(Color.WHITE);
            } else {
                setForeground(new Color(0x2D3748));
            }
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            if (selected) {
                g2.setColor(new Color(0x4A90D9));
                g2.fillRoundRect(4, 0, getWidth() - 8, getHeight(), 8, 8);
            }
            
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
