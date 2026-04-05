package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.config.GlobalConfigurationStore;
import se.goencoder.loppiskassan.controller.CashierTabController;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.service.BackgroundSyncManager;
import se.goencoder.loppiskassan.service.CashierHeartbeatService;
import se.goencoder.loppiskassan.service.RegisterSessionManager;
import se.goencoder.loppiskassan.service.RegisterSessionState;
import se.goencoder.loppiskassan.service.RejectedItemsManager;
import se.goencoder.loppiskassan.storage.PendingItemsStore;
import se.goencoder.loppiskassan.ui.dialogs.PendingItemsDialog;
import se.goencoder.loppiskassan.ui.dialogs.RejectedItemsDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * App Shell frame för Loppiskassan 3.0.
 * Ersätter UserInterface med modern layout: Topbar + Sidebar + Huvudinnehåll + Statusrad.
 */
public class AppShellFrame extends JFrame implements LocalizationAware {
    
    private final LocalizationManager.LanguageChangeListener languageChangeListener = this::reloadTexts;
    private final AppShellTopbar topbar;
    private final AppShellSidebar sidebar;
    private final AppShellStatusbar statusbar;
    private final JPanel contentPanel;
    
    // Vyer som visas i huvudinnehållet
    private JPanel currentView;
    private JPanel cashierView;
    private JPanel historyView;
    private JPanel exportView;
    private JPanel supportView;
    private JPanel archiveView;
    private JPanel discoveryView;
    private JPanel recentPurchasesView;
    private Integer pendingCountCache;
    
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
        
        // Wire up pending count listener for statusbar in iLoppis mode
        if (!AppModeManager.isLocalMode()) {
            BackgroundSyncManager.getInstance().setPendingCountListener(count -> {
                statusbar.setPendingStatus(count);
                pendingCountCache = count;
            });
            RejectedItemsManager.getInstance().setRejectedCountListener(statusbar::setRejectedStatus);

            statusbar.setPendingClickListener(() ->
                    PendingItemsDialog.show(this, AppModeManager.getEventId()));
            statusbar.setRejectedClickListener(() ->
                    RejectedItemsDialog.show(this, AppModeManager.getEventId()));

            refreshStatusIndicators();
            ensureOnlineSessionInitialized();
        }
        
        // Visa första vyn beroende på om evenemang är valt
        if (AppModeManager.getEventId() == null) {
            navigateTo(NavigationTarget.DISCOVERY);
        } else {
            navigateTo(NavigationTarget.CASHIER);
        }
        
        // Registrera för språkändringar
        LocalizationManager.addListener(languageChangeListener);
        
        setTitle(LocalizationManager.tr("frame.title"));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleWindowClose();
            }
        });
        setSize(1024, 700);
        setLocationRelativeTo(null);
    }

    private void refreshStatusIndicators() {
        if (eventId == null || eventId.isBlank()) {
            exitApplication();
            return;
        }
        RegisterSessionManager.SessionData session = RegisterSessionManager.getInstance().getCurrent();
        if (session == null) {
            exitApplication();
            return;
        }

        String displayName = GlobalConfigurationStore.getCashierName();
        if (displayName == null || displayName.isBlank()) {
            displayName = LocalizationManager.tr("register.default_name");
        }

        String finalDisplayName = displayName;
        String registerId = session.registerId;
        String sessionId = session.sessionId;

        JDialog closingDialog = new JDialog(
                this,
                LocalizationManager.tr("exit.session_closing.title"),
                false
        );
        closingDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JLabel closingLabel = new JLabel(
                LocalizationManager.tr("exit.session_closing.message"),
                SwingConstants.CENTER
        );
        closingLabel.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        closingDialog.add(closingLabel);
        closingDialog.pack();
        closingDialog.setLocationRelativeTo(this);
        closingDialog.setVisible(true);

        Timer timeout = new Timer(5_000, event -> exitApplication());
        timeout.setRepeats(false);
        timeout.start();

        Runnable closeProgressDialog = () -> {
            timeout.stop();
            closingDialog.dispose();
        };

        RegisterSessionState sessionState = session.state;
        Thread closeHandshakeThread = new Thread(() -> {
            CashierHeartbeatService heartbeatService = new CashierHeartbeatService();
            try {
                RegisterSessionManager sessionManager = RegisterSessionManager.getInstance();
                if (sessionState == RegisterSessionState.OPEN) {
                    boolean closeRequestedSent = heartbeatService.sendHeartbeat(
                            eventId,
                            "CASHIER_CLIENT_STATE_IDLE",
                            0,
                            "CASHIER_CLIENT_TYPE_JAVA",
                            finalDisplayName,
                            "REGISTER_LIFECYCLE_EVENT_TYPE_CLOSE_REQUESTED",
                            registerId,
                            sessionId
                    ).success();
                    if (!closeRequestedSent) {
                        throw new IllegalStateException("close-requested heartbeat was not acknowledged");
                    }
                    sessionManager.requestClose();
                } else if (sessionState != RegisterSessionState.CLOSE_REQUESTED) {
                    SwingUtilities.invokeLater(() -> {
                        closeProgressDialog.run();
                        exitApplication();
                    });
                    return;
                }

                boolean closeConfirmedSent = heartbeatService.sendHeartbeat(
                        eventId,
                        "CASHIER_CLIENT_STATE_IDLE",
                        0,
                        "CASHIER_CLIENT_TYPE_JAVA",
                        finalDisplayName,
                        "REGISTER_LIFECYCLE_EVENT_TYPE_CLOSE_CONFIRMED",
                        registerId,
                        sessionId
                ).success();
                if (!closeConfirmedSent) {
                    throw new IllegalStateException("close-confirmed heartbeat was not acknowledged");
                }
                sessionManager.confirmClose();
                SwingUtilities.invokeLater(() -> {
                    closeProgressDialog.run();
                    exitApplication();
                });
            } catch (Exception ignored) {
                SwingUtilities.invokeLater(() -> Popup.ERROR.showAndWait(
                        LocalizationManager.tr("exit.session_open.close_failed.title"),
                        LocalizationManager.tr("exit.session_open.close_failed.message")
                ));
                SwingUtilities.invokeLater(closeProgressDialog);
            }
        }, "close-handshake-heartbeat");
        closeHandshakeThread.setDaemon(true);
        closeHandshakeThread.start();
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
        panel.setBackground(AppColors.WHITE);
        
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AppColors.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(AppColors.BORDER, 1),
            BorderFactory.createEmptyBorder(32, 32, 32, 32)
        ));
        
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JLabel descLabel = new JLabel(description);
        descLabel.setFont(descLabel.getFont().deriveFont(13f));
        descLabel.setForeground(AppColors.TEXT_MUTED);
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
        LocalizationManager.removeListener(languageChangeListener);
        super.removeNotify();
    }

    /**
     * ILP-003-06: Exit guard — intercepts window close when a register session is active
     * or there are unsynced pending items.
     *
     * <p>If both conditions are absent the window closes normally.
     * If unsynced items exist the user must confirm before exit; the session is left as-is
     * so it can be recovered on next launch.
    * If the session can be closed cleanly (no pending items) the close handshake must
    * complete before the JVM exits so local and backend session state stay aligned.</p>
     */
    private void handleWindowClose() {
        if (AppModeManager.isLocalMode()) {
            exitApplication();
            return;
        }

        String eventId = AppModeManager.getEventId();
        if (eventId == null || eventId.isBlank()) {
            exitApplication();
            return;
        }
        boolean sessionActive = RegisterSessionManager.getInstance().isSessionActive();
        Integer pendingCount = pendingCountCache;
        boolean pendingReadFailed = pendingCount == null;

        if (!sessionActive && pendingCount != null && pendingCount == 0) {
            exitApplication();
            return;
        }

        if (pendingReadFailed) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    LocalizationManager.tr("exit.pending_sync.read_failed"),
                    LocalizationManager.tr("exit.pending_sync.read_failed_title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
        } else if (pendingCount != null && pendingCount > 0) {
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    LocalizationManager.tr("exit.pending_sync.message", pendingCount),
                    LocalizationManager.tr("exit.pending_sync.title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return; // abort close
            }
        } else if (sessionActive) {
            // Session open but no pending items — offer clean close
            int choice = JOptionPane.showConfirmDialog(
                    this,
                    LocalizationManager.tr("exit.session_open.message"),
                    LocalizationManager.tr("exit.session_open.title"),
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return;
            }
            // Best-effort: fire close handshake heartbeats before exiting.
            startCloseHandshakeAndExit(eventId);
            return;
        }

        exitApplication();
    }

    private void exitApplication() {
        dispose();
        System.exit(0);
    }

    private void ensureOnlineSessionInitialized() {
        if (AppModeManager.isLocalMode()) {
            return;
        }
        String eventId = AppModeManager.getEventId();
        if (eventId == null || eventId.isBlank()) {
            return;
        }

        BackgroundSyncManager.getInstance().ensureRunning(eventId);
        RegisterSessionManager sessionMgr = RegisterSessionManager.getInstance();
        sessionMgr.loadOrRecover(eventId);
        String registerName = GlobalConfigurationStore.getCashierName();
        if (registerName == null || registerName.isBlank()) {
            registerName = LocalizationManager.tr("register.default_name");
        }
        sessionMgr.openSession(eventId, registerName);
    }

    private void startCloseHandshakeAndExit(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            exitApplication();
            return;
        }
        RegisterSessionManager.SessionData session = RegisterSessionManager.getInstance().getCurrent();
        if (session == null) {
            exitApplication();
            return;
        }

        String displayName = GlobalConfigurationStore.getCashierName();
        if (displayName == null || displayName.isBlank()) {
            displayName = LocalizationManager.tr("register.default_name");
        }

        String finalDisplayName = displayName;
        String registerId = session.registerId;
        String sessionId = session.sessionId;
        JDialog closingDialog = new JDialog(
                this,
                LocalizationManager.tr("exit.session_closing.title"),
                false
        );
        closingDialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        JLabel closingLabel = new JLabel(
                LocalizationManager.tr("exit.session_closing.message"),
                SwingConstants.CENTER
        );
        closingLabel.setBorder(BorderFactory.createEmptyBorder(16, 24, 16, 24));
        closingDialog.add(closingLabel);
        closingDialog.pack();
        closingDialog.setLocationRelativeTo(this);
        closingDialog.setVisible(true);

        Timer timeout = new Timer(5_000, event -> exitApplication());
        timeout.setRepeats(false);
        timeout.start();
        Thread closeHandshakeThread = new Thread(() -> {
            CashierHeartbeatService heartbeatService = new CashierHeartbeatService();
            try {
                boolean closeRequestedSent = heartbeatService.sendHeartbeat(
                        eventId,
                        "CASHIER_CLIENT_STATE_IDLE",
                        0,
                        "CASHIER_CLIENT_TYPE_JAVA",
                Runnable closeProgressDialog = () -> {
                    timeout.stop();
                    closingDialog.dispose();
                };

                RegisterSessionState sessionState = session.state;
                        finalDisplayName,
                        "REGISTER_LIFECYCLE_EVENT_TYPE_CLOSE_REQUESTED",
                        registerId,
                        RegisterSessionManager sessionManager = RegisterSessionManager.getInstance();
                        if (sessionState == RegisterSessionState.OPEN) {
                            boolean closeRequestedSent = heartbeatService.sendHeartbeat(
                                    eventId,
                                    "CASHIER_CLIENT_STATE_IDLE",
                                    0,
                                    "CASHIER_CLIENT_TYPE_JAVA",
                                    finalDisplayName,
                                    "REGISTER_LIFECYCLE_EVENT_TYPE_CLOSE_REQUESTED",
                                    registerId,
                                    sessionId
                            ).success();
                            if (!closeRequestedSent) {
                                throw new IllegalStateException("close-requested heartbeat was not acknowledged");
                            }
                            sessionManager.requestClose();
                        } else if (sessionState != RegisterSessionState.CLOSE_REQUESTED) {
                            SwingUtilities.invokeLater(() -> {
                                closeProgressDialog.run();
                                exitApplication();
                            });
                            return;
                        }

                        boolean closeConfirmedSent = heartbeatService.sendHeartbeat(
                ).success();
                boolean closeConfirmedSent = closeRequestedSent && heartbeatService.sendHeartbeat(
=======
        RegisterSessionState sessionState = session.state;
        Thread closeHandshakeThread = new Thread(() -> {
                                "REGISTER_LIFECYCLE_EVENT_TYPE_CLOSE_CONFIRMED",
            try {
            RegisterSessionManager sessionManager = RegisterSessionManager.getInstance();
            if (sessionState == RegisterSessionState.OPEN) {
                        if (!closeConfirmedSent) {
                            throw new IllegalStateException("close-confirmed heartbeat was not acknowledged");

                        SwingUtilities.invokeLater(() -> {
                            closeProgressDialog.run();
                            exitApplication();
                        });
                closingDialog.dispose();
                exitApplication();
            });
=======
>>>>>>> f4c17f7 (fix: keep register close state aligned with backend)
                        SwingUtilities.invokeLater(closeProgressDialog);
        }, "close-handshake-heartbeat");
        closeHandshakeThread.setDaemon(true);
        closeHandshakeThread.start();
    }

    /**
     * Navigationsmål i applikationen.
     */
    enum NavigationTarget {
        DISCOVERY,
        CASHIER,
        RECENT,
        HISTORY,
        EXPORT,
        SUPPORT,
        ARCHIVE
    }
}
