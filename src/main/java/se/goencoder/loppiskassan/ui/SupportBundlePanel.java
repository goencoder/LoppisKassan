package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.controller.DataBundleExporter;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.localization.LocalizationManager;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;

/**
 * Dedicated support page for exporting an iLoppis troubleshooting bundle.
 */
public class SupportBundlePanel extends JPanel implements LocalizationAware {

    private final LocalizationManager.LanguageChangeListener languageChangeListener = this::reloadTexts;

    private JLabel titleLabel;
    private JTextArea descriptionArea;
    private JLabel detailsTitleLabel;
    private JLabel eventLabel;
    private JTextField eventIdField;
    private JTextArea includesArea;
    private JLabel actionTitleLabel;
    private JTextArea actionHelpArea;
    private JLabel sendToLabel;
    private JButton exportButton;

    public SupportBundlePanel() {
        setLayout(new BorderLayout());
        setBackground(AppColors.WHITE);
        setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));

        JPanel canvas = new JPanel(new BorderLayout());
        canvas.setBackground(AppColors.WHITE);

        JPanel shell = new JPanel();
        shell.setLayout(new BoxLayout(shell, BoxLayout.Y_AXIS));
        shell.setBackground(AppColors.WHITE);
        shell.setPreferredSize(new Dimension(840, 360));

        JPanel headerCard = createCard();
        headerCard.setLayout(new BoxLayout(headerCard, BoxLayout.Y_AXIS));

        titleLabel = new JLabel();
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 22f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerCard.add(titleLabel);
        headerCard.add(Box.createVerticalStrut(10));

        descriptionArea = createBodyTextArea();
        descriptionArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerCard.add(descriptionArea);

        shell.add(headerCard);
        shell.add(Box.createVerticalStrut(20));

        JPanel contentGrid = new JPanel(new GridLayout(1, 2, 20, 0));
        contentGrid.setBackground(AppColors.WHITE);
        contentGrid.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 260));

        JPanel detailsCard = createTintedCard(AppColors.SURFACE);
        detailsCard.setLayout(new BoxLayout(detailsCard, BoxLayout.Y_AXIS));

        detailsTitleLabel = new JLabel();
        detailsTitleLabel.setFont(detailsTitleLabel.getFont().deriveFont(Font.BOLD, 16f));
        detailsTitleLabel.setForeground(AppColors.TEXT_PRIMARY);
        detailsTitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailsCard.add(detailsTitleLabel);
        detailsCard.add(Box.createVerticalStrut(16));

        eventLabel = new JLabel();
        eventLabel.setFont(eventLabel.getFont().deriveFont(Font.PLAIN, 12f));
        eventLabel.setForeground(AppColors.TEXT_MUTED);
        eventLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailsCard.add(eventLabel);
        detailsCard.add(Box.createVerticalStrut(8));

        eventIdField = new JTextField();
        eventIdField.setEditable(false);
        eventIdField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        eventIdField.setBackground(AppColors.WHITE);
        eventIdField.setForeground(AppColors.TEXT_PRIMARY);
        eventIdField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColors.BORDER, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        eventIdField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        eventIdField.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailsCard.add(eventIdField);
        detailsCard.add(Box.createVerticalStrut(16));

        includesArea = createBodyTextArea();
        includesArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        detailsCard.add(includesArea);
        detailsCard.add(Box.createVerticalGlue());

        JPanel actionCard = createTintedCard(AppColors.FIELD_BG);
        actionCard.setLayout(new BoxLayout(actionCard, BoxLayout.Y_AXIS));

        actionTitleLabel = new JLabel();
        actionTitleLabel.setFont(actionTitleLabel.getFont().deriveFont(Font.BOLD, 16f));
        actionTitleLabel.setForeground(AppColors.TEXT_PRIMARY);
        actionTitleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionCard.add(actionTitleLabel);
        actionCard.add(Box.createVerticalStrut(16));

        actionHelpArea = createBodyTextArea();
        actionHelpArea.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionCard.add(actionHelpArea);
        actionCard.add(Box.createVerticalStrut(16));

        sendToLabel = new JLabel();
        sendToLabel.setOpaque(true);
        sendToLabel.setBackground(AppColors.WHITE);
        sendToLabel.setForeground(AppColors.TEXT_SECONDARY);
        sendToLabel.setFont(sendToLabel.getFont().deriveFont(Font.BOLD, 12f));
        sendToLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColors.BORDER, 1),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)
        ));
        sendToLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        actionCard.add(sendToLabel);
        actionCard.add(Box.createVerticalGlue());
        actionCard.add(Box.createVerticalStrut(16));

        exportButton = AppButton.create("", AppButton.Variant.PRIMARY, AppButton.Size.LARGE);
        exportButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        exportButton.addActionListener(e -> handleExport());
        actionCard.add(exportButton);

        contentGrid.add(detailsCard);
        contentGrid.add(actionCard);
        shell.add(contentGrid);

        canvas.add(shell, BorderLayout.NORTH);
        add(canvas, BorderLayout.CENTER);

        LocalizationManager.addListener(languageChangeListener);
        reloadTexts();
    }

    private JPanel createCard() {
        JPanel panel = new JPanel();
        panel.setBackground(AppColors.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColors.BORDER, 1),
                BorderFactory.createEmptyBorder(24, 24, 24, 24)
        ));
        return panel;
    }

    private JPanel createTintedCard(java.awt.Color background) {
        JPanel panel = new JPanel();
        panel.setBackground(background);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColors.BORDER, 1),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));
        return panel;
    }

    private JTextArea createBodyTextArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFocusable(false);
        area.setOpaque(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(area.getFont().deriveFont(Font.PLAIN, 13f));
        area.setForeground(AppColors.TEXT_MUTED);
        area.setAlignmentX(Component.LEFT_ALIGNMENT);
        return area;
    }

    private void handleExport() {
        String eventId = AppModeManager.getEventId();
        if (eventId == null || eventId.isBlank()) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("error.no_event_selected.title"),
                    LocalizationManager.tr("error.no_event_selected.message"));
            return;
        }

        DataBundleExporter.exportBundle(eventId, null);
    }

    @Override
    public void reloadTexts() {
        titleLabel.setText(LocalizationManager.tr("support_bundle.section.title"));
        descriptionArea.setText(LocalizationManager.tr("support_bundle.section.description"));
        detailsTitleLabel.setText(LocalizationManager.tr("support_bundle.details.title"));
        eventLabel.setText(LocalizationManager.tr("support_bundle.event_id"));
        includesArea.setText(LocalizationManager.tr("support_bundle.includes"));
        actionTitleLabel.setText(LocalizationManager.tr("support_bundle.action.title"));
        actionHelpArea.setText(LocalizationManager.tr("support_bundle.dialog.tip"));
        sendToLabel.setText(LocalizationManager.tr("support_bundle.send_to"));
        exportButton.setText(LocalizationManager.tr("support_bundle.button"));
        eventIdField.setText(AppModeManager.getEventId() != null ? AppModeManager.getEventId() : "-");
        eventIdField.setCaretPosition(0);
    }

    @Override
    public void removeNotify() {
        LocalizationManager.removeListener(languageChangeListener);
        super.removeNotify();
    }
}
