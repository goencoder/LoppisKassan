package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.controller.DataBundleExporter;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.localization.LocalizationManager;

import javax.swing.*;
import java.awt.*;

/**
 * Dedicated support page for exporting an iLoppis troubleshooting bundle.
 */
public class SupportBundlePanel extends JPanel implements LocalizationAware {

    private static final int CARD_WIDTH = 760;
    private static final int TEXT_WIDTH = 620;

    private JLabel titleLabel;
    private JLabel descriptionLabel;
    private JLabel eventLabel;
    private JLabel eventIdLabel;
    private JLabel includesLabel;
    private JButton exportButton;

    public SupportBundlePanel() {
        setLayout(new BorderLayout());
        setBackground(AppColors.WHITE);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        contentPanel.setBackground(AppColors.WHITE);
        contentPanel.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(AppColors.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColors.BORDER, 1),
                BorderFactory.createEmptyBorder(28, 32, 28, 32)
        ));
        card.setMaximumSize(new Dimension(CARD_WIDTH, Integer.MAX_VALUE));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        titleLabel = new JLabel();
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(titleLabel);

        card.add(Box.createVerticalStrut(8));

        descriptionLabel = new JLabel();
        descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(13f));
        descriptionLabel.setForeground(AppColors.TEXT_MUTED);
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(descriptionLabel);

        card.add(Box.createVerticalStrut(24));

        JPanel infoPanel = new JPanel(new GridBagLayout());
        infoPanel.setBackground(AppColors.WHITE);
        infoPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, AppColors.BORDER),
                BorderFactory.createEmptyBorder(16, 0, 16, 0)
        ));
        infoPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

        eventLabel = new JLabel();
        eventLabel.setFont(eventLabel.getFont().deriveFont(Font.BOLD, 12f));
        eventLabel.setForeground(AppColors.TEXT_MUTED);

        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.gridx = 0;
        labelGbc.gridy = 0;
        labelGbc.anchor = GridBagConstraints.WEST;
        labelGbc.insets = new Insets(0, 0, 0, 16);
        infoPanel.add(eventLabel, labelGbc);

        eventIdLabel = new JLabel();
        eventIdLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

        GridBagConstraints valueGbc = new GridBagConstraints();
        valueGbc.gridx = 1;
        valueGbc.gridy = 0;
        valueGbc.weightx = 1.0;
        valueGbc.fill = GridBagConstraints.HORIZONTAL;
        valueGbc.anchor = GridBagConstraints.WEST;
        infoPanel.add(eventIdLabel, valueGbc);

        card.add(infoPanel);
        card.add(Box.createVerticalStrut(24));

        includesLabel = new JLabel();
        includesLabel.setFont(includesLabel.getFont().deriveFont(Font.PLAIN, 12f));
        includesLabel.setForeground(AppColors.TEXT_MUTED);
        includesLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.add(includesLabel);

        card.add(Box.createVerticalStrut(16));

        exportButton = AppButton.create("", AppButton.Variant.PRIMARY, AppButton.Size.MEDIUM);
        exportButton.addActionListener(e -> handleExport());

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        buttonRow.setBackground(AppColors.WHITE);
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonRow.add(exportButton);
        card.add(buttonRow);

        contentPanel.add(card);
        contentPanel.add(Box.createVerticalGlue());

        JScrollPane scrollPane = new JScrollPane(contentPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(AppColors.WHITE);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        add(scrollPane, BorderLayout.CENTER);

        LocalizationManager.addListener(this::reloadTexts);
        reloadTexts();
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
        descriptionLabel.setText(asWrappedHtml(LocalizationManager.tr("support_bundle.section.description")));
        eventLabel.setText(LocalizationManager.tr("support_bundle.event_id"));
        includesLabel.setText(asWrappedHtml(LocalizationManager.tr("support_bundle.includes")));
        exportButton.setText(LocalizationManager.tr("support_bundle.button"));
        eventIdLabel.setText(AppModeManager.getEventId() != null ? AppModeManager.getEventId() : "-");
    }

    private static String asWrappedHtml(String text) {
        return "<html><div style='width: " + TEXT_WIDTH + "px;'>" + text + "</div></html>";
    }

    @Override
    public void removeNotify() {
        LocalizationManager.removeListener(this::reloadTexts);
        super.removeNotify();
    }
}
