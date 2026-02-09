package se.goencoder.loppiskassan.ui.dialogs;

import se.goencoder.loppiskassan.config.AppMode;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.ui.LanguageSelector;
import se.goencoder.loppiskassan.ui.icons.MonitorIcon;
import se.goencoder.loppiskassan.ui.icons.ShopIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Splash screen shown at application startup.
 * Lets the user choose between <b>Local</b> (offline) and <b>iLoppis</b> (online) mode.
 * Returns the selected {@link AppMode}, or {@code null} if the window is closed.
 */
public class ModeSelectionDialog extends JDialog {

    private AppMode selectedMode;

    // References for live language updates
    private JLabel titleLabel;
    private JLabel subtitleLabel;
    private JLabel localTitleLabel;
    private JLabel localDescLabel;
    private JLabel iloppisTitleLabel;
    private JLabel iloppisDescLabel;
    private final LocalizationManager.LanguageChangeListener reloadTexts = this::updateTexts;

    public ModeSelectionDialog() {
        super((Frame) null, LocalizationManager.tr("frame.title"), true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        buildUI();
        LocalizationManager.addListener(reloadTexts);
        pack();
        setLocationRelativeTo(null);
    }

    /**
     * Show the dialog and block until the user picks a mode.
     * @return the chosen mode, or {@code null} if the dialog was closed.
     */
    public AppMode showDialog() {
        setVisible(true);
        LocalizationManager.removeListener(reloadTexts);
        return selectedMode;
    }

    private void updateTexts() {
        setTitle(LocalizationManager.tr("frame.title"));
        titleLabel.setText(LocalizationManager.tr("splash.title"));
        subtitleLabel.setText(LocalizationManager.tr("splash.subtitle"));
        localTitleLabel.setText(LocalizationManager.tr("splash.local"));
        localDescLabel.setText("<html><div style='text-align:center; width:140px;'>"
                + LocalizationManager.tr("splash.local.desc") + "</div></html>");
        iloppisTitleLabel.setText(LocalizationManager.tr("splash.iloppis"));
        iloppisDescLabel.setText("<html><div style='text-align:center; width:140px;'>"
                + LocalizationManager.tr("splash.iloppis.desc") + "</div></html>");
        pack();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBorder(new EmptyBorder(24, 32, 24, 32));

        // Language selector in top-right
        JPanel topBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        topBar.setOpaque(false);
        topBar.add(new LanguageSelector());
        root.add(topBar, BorderLayout.NORTH);

        // Title
        titleLabel = new JLabel(LocalizationManager.tr("splash.title"), SwingConstants.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
        titleLabel.setBorder(new EmptyBorder(0, 0, 4, 0));

        subtitleLabel = new JLabel(LocalizationManager.tr("splash.subtitle"), SwingConstants.CENTER);
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 13f));
        subtitleLabel.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel headerPanel = new JPanel();
        headerPanel.setLayout(new BoxLayout(headerPanel, BoxLayout.Y_AXIS));
        headerPanel.setOpaque(false);
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        subtitleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        headerPanel.add(titleLabel);
        headerPanel.add(Box.createVerticalStrut(4));
        headerPanel.add(subtitleLabel);

        // Mode buttons
        JPanel buttonsPanel = new JPanel(new GridLayout(1, 2, 24, 0));
        buttonsPanel.setOpaque(false);
        buttonsPanel.setBorder(new EmptyBorder(20, 0, 8, 0));

        JButton localButton = createModeButton(
                new MonitorIcon(48, new Color(0x4A90D9)),
                LocalizationManager.tr("splash.local"),
                LocalizationManager.tr("splash.local.desc")
        );
        // Grab label refs from the button's children
        localTitleLabel = (JLabel) localButton.getComponent(1);
        localDescLabel = (JLabel) localButton.getComponent(2);
        localButton.addActionListener(e -> {
            selectedMode = AppMode.LOCAL;
            dispose();
        });

        JButton iloppisButton = createModeButton(
                new ShopIcon(48, new Color(0xE05A5A)),
                LocalizationManager.tr("splash.iloppis"),
                LocalizationManager.tr("splash.iloppis.desc")
        );
        iloppisTitleLabel = (JLabel) iloppisButton.getComponent(1);
        iloppisDescLabel = (JLabel) iloppisButton.getComponent(2);
        iloppisButton.addActionListener(e -> {
            selectedMode = AppMode.ILOPPIS;
            dispose();
        });

        buttonsPanel.add(localButton);
        buttonsPanel.add(iloppisButton);

        // Center area
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.setOpaque(false);
        headerPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        buttonsPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        centerPanel.add(headerPanel);
        centerPanel.add(buttonsPanel);

        root.add(centerPanel, BorderLayout.CENTER);
        setContentPane(root);
    }

    /**
     * Create a large square button with an icon, title, and description.
     */
    private static JButton createModeButton(Icon icon, String label, String description) {
        JButton button = new JButton();
        button.setLayout(new BoxLayout(button, BoxLayout.Y_AXIS));
        button.setPreferredSize(new Dimension(200, 180));
        button.setFocusPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Icon
        JLabel iconLabel = new JLabel(icon);
        iconLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        iconLabel.setBorder(new EmptyBorder(16, 0, 8, 0));

        // Title
        JLabel titleLabel = new JLabel(label);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Description
        JLabel descLabel = new JLabel("<html><div style='text-align:center; width:140px;'>"
                + description + "</div></html>");
        descLabel.setFont(descLabel.getFont().deriveFont(Font.PLAIN, 11f));
        descLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        descLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        descLabel.setBorder(new EmptyBorder(4, 8, 12, 8));

        button.add(iconLabel);
        button.add(titleLabel);
        button.add(descLabel);

        return button;
    }
}
