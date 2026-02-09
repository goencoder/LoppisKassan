package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.config.ConfigurationStore;
import se.goencoder.loppiskassan.controller.CashierTabController;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.localization.LocalizationManager;

import javax.swing.*;
import java.awt.*;

/**
 * App Shell frame för Loppiskassan 3.0.
 * Ersätter UserInterface med modern layout: Topbar + Sidebar + Huvudinnehåll + Statusrad.
 */
public class AppShellFrame extends JFrame implements LocalizationAware {
    
    private final AppShellTopbar topbar;
    private final AppShellSidebar sidebar;
    private final AppShellStatusbar statusbar;
    private final JPanel contentPanel;
    
    // Vyer som visas i huvudinnehållet
    private JPanel currentView;
    private JPanel cashierView;
    private JPanel historyView;
    private JPanel exportView;
    private JPanel archiveView;
    private JPanel discoveryView;
    
    public AppShellFrame() {
        setLayout(new BorderLayout());
        setAppIcon();
        
        // Skapa komponenter
        topbar = new AppShellTopbar();
        sidebar = new AppShellSidebar(this::navigateTo);
        statusbar = new AppShellStatusbar();
        contentPanel = new JPanel(new BorderLayout());
        
        // Bygg layout
        add(topbar, BorderLayout.NORTH);
        add(sidebar, BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);
        add(statusbar, BorderLayout.SOUTH);
        
        // Initiera vyer
        initializeViews();
        
        // Visa första vyn beroende på om evenemang är valt
        if (ConfigurationStore.EVENT_ID_STR.get() == null) {
            navigateTo(NavigationTarget.DISCOVERY);
        } else {
            navigateTo(NavigationTarget.CASHIER);
        }
        
        // Registrera för språkändringar
        LocalizationManager.addListener(this::reloadTexts);
        
        setTitle(LocalizationManager.tr("frame.title"));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1024, 700);
        setLocationRelativeTo(null);
    }
    
    private void initializeViews() {
        // Discovery-vy (evenemangsval)
        if (AppModeManager.isLocalMode()) {
            discoveryView = new LocalDiscoveryTabPanel();
        } else {
            discoveryView = new DiscoveryTabPanel();
        }
        
        // Kassavy
        cashierView = new CashierTabPanel(CashierTabController.getInstance());
        
        // Historikvy
        historyView = new HistoryTabPanel();
        
        // Export/Import-vy (endast lokal kassa)
        if (AppModeManager.isLocalMode()) {
            exportView = new ExportImportTabPanel();
        }
        
        // Arkivvy (endast lokal kassa)
        if (AppModeManager.isLocalMode()) {
            archiveView = new ArchiveTabPanel();
        }
    }
    
    /**
     * Navigerar till angiven vy.
     */
    void navigateTo(NavigationTarget target) {
        // Validera att evenemang är valt (utom för discovery)
        if (target != NavigationTarget.DISCOVERY && ConfigurationStore.EVENT_ID_STR.get() == null) {
            Popup.ERROR.showAndWait(
                LocalizationManager.tr("error.no_event_selected.title"),
                LocalizationManager.tr("error.no_event_selected.message"));
            navigateTo(NavigationTarget.DISCOVERY);
            sidebar.setSelected(NavigationTarget.DISCOVERY);
            return;
        }
        
        JPanel targetView = switch (target) {
            case DISCOVERY -> discoveryView;
            case CASHIER -> cashierView;
            case HISTORY -> historyView;
            case EXPORT -> exportView;
            case ARCHIVE -> archiveView;
        };
        
        if (targetView == null) {
            return; // Vy saknas (t.ex. export i iLoppis-läge)
        }
        
        // Byt vy
        contentPanel.removeAll();
        contentPanel.add(targetView, BorderLayout.CENTER);
        contentPanel.revalidate();
        contentPanel.repaint();
        
        currentView = targetView;
        
        // Notifiera vy om selection (för t.ex. fokushantering)
        if (targetView instanceof SelectabableTab selectable) {
            selectable.selected();
        }
        
        // Uppdatera sidebar-markering
        sidebar.setSelected(target);
    }
    
    private JPanel createPlaceholderView(String title, String description) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(Color.WHITE);
        
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(0xE2E8F0), 1),
            BorderFactory.createEmptyBorder(32, 32, 32, 32)
        ));
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel descLabel = new JLabel(description);
        descLabel.setFont(descLabel.getFont().deriveFont(13f));
        descLabel.setForeground(new Color(0x718096));
        descLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        descLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        
        card.add(titleLabel);
        card.add(descLabel);
        
        panel.add(card);
        return panel;
    }
    
    private void setAppIcon() {
        var iconImage = se.goencoder.loppiskassan.Main.getAppIconImage();
        if (iconImage == null) {
            try {
                var iconUrl = getClass().getClassLoader().getResource("images/iloppis-icon.png");
                if (iconUrl != null) {
                    iconImage = javax.imageio.ImageIO.read(iconUrl);
                }
            } catch (Exception e) {
                System.err.println("Failed to load app icon: " + e.getMessage());
            }
        }
        if (iconImage != null) {
            setIconImage(iconImage);
        }
    }
    
    @Override
    public void reloadTexts() {
        setTitle(LocalizationManager.tr("frame.title"));
        topbar.reloadTexts();
        sidebar.reloadTexts();
        statusbar.reloadTexts();
        
        // Uppdatera aktiv vy om den stödjer localization
        if (currentView instanceof LocalizationAware aware) {
            aware.reloadTexts();
        }
    }
    
    @Override
    public void removeNotify() {
        LocalizationManager.removeListener(this::reloadTexts);
        super.removeNotify();
    }
    
    /**
     * Navigationsmål i applikationen.
     */
    enum NavigationTarget {
        DISCOVERY,
        CASHIER,
        HISTORY,
        EXPORT,
        ARCHIVE
    }
}
