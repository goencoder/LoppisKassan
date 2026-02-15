package se.goencoder.loppiskassan.ui.dialogs;

import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.ui.AppButton;
import se.goencoder.loppiskassan.ui.AppColors;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.util.AppPaths;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Settings dialog that shows where all local files are stored.
 */
public final class SettingsDialog extends JDialog implements LocalizationAware {

    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel subtitleLabel;
    private final JLabel hintLabel;

    public static void show(Component parent) {
        SettingsDialog dialog = new SettingsDialog(parent);
        dialog.setVisible(true);
    }

    private SettingsDialog(Component parent) {
        super(SwingUtilities.getWindowAncestor(parent));

        setModal(true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(LocalizationManager.tr("settings.dialog.title"));
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColors.WHITE);

        JLabel titleLabel = new JLabel(LocalizationManager.tr("settings.dialog.title"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        titleLabel.setForeground(AppColors.TEXT_PRIMARY);

        subtitleLabel = new JLabel(LocalizationManager.tr("settings.dialog.subtitle"));
        subtitleLabel.setFont(subtitleLabel.getFont().deriveFont(Font.PLAIN, 12f));
        subtitleLabel.setForeground(AppColors.TEXT_MUTED);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppColors.WHITE);
        header.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        header.add(titleLabel, BorderLayout.NORTH);
        header.add(subtitleLabel, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        tableModel = new DefaultTableModel(null, buildColumnNames()) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(26);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.putClientProperty("Table.alternateRowColor", Boolean.TRUE);

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.LEFT);
        table.getColumnModel().getColumn(0).setCellRenderer(center);

        DefaultTableCellRenderer pathRenderer = new DefaultTableCellRenderer();
        pathRenderer.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        pathRenderer.setForeground(AppColors.TEXT_PRIMARY);
        table.getColumnModel().getColumn(1).setCellRenderer(pathRenderer);

        table.getColumnModel().getColumn(0).setPreferredWidth(220);
        table.getColumnModel().getColumn(1).setPreferredWidth(620);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(AppColors.WHITE);
        scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, AppColors.BORDER));

        hintLabel = new JLabel();
        hintLabel.setFont(hintLabel.getFont().deriveFont(Font.PLAIN, 11f));
        hintLabel.setForeground(AppColors.TEXT_MUTED);
        hintLabel.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(AppColors.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        content.add(scrollPane, BorderLayout.CENTER);
        content.add(hintLabel, BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);

        JButton copyButton = AppButton.create(
                LocalizationManager.tr("settings.copy_selected"),
                AppButton.Variant.SECONDARY,
                AppButton.Size.MEDIUM);
        copyButton.addActionListener(evt -> copySelectedPath());

        JButton closeButton = AppButton.create(
                LocalizationManager.tr("button.close"),
                AppButton.Variant.SECONDARY,
                AppButton.Size.MEDIUM);
        closeButton.addActionListener(evt -> dispose());

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 12));
        footer.setBackground(AppColors.WHITE);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));
        footer.add(copyButton);
        footer.add(closeButton);
        add(footer, BorderLayout.SOUTH);

        loadRows();

        setSize(new Dimension(900, 520));
        setLocationRelativeTo(parent);
        LocalizationManager.addListener(this::reloadTexts);
    }

    private void loadRows() {
        tableModel.setRowCount(0);
        for (PathRow row : buildRows()) {
            tableModel.addRow(new Object[]{row.label, row.path});
        }
        hintLabel.setText(buildHintText());
    }

    private List<PathRow> buildRows() {
        List<PathRow> rows = new ArrayList<>();

        String eventId = AppModeManager.getEventId();
        String eventToken = (eventId == null || eventId.isBlank())
                ? LocalizationManager.tr("settings.placeholder.event_id")
                : eventId;

        Path baseDir = AppPaths.getBaseDir();
        Path configDir = AppPaths.getConfigDir();
        Path logsDir = AppPaths.getLogsDir();
        Path dataDir = AppPaths.getDataDir();
        Path eventsDir = LocalEventPaths.getEventsDir();
        Path eventDir = eventsDir.resolve(eventToken);

        rows.add(new PathRow(LocalizationManager.tr("settings.row.base_dir"), baseDir.toString()));
        rows.add(new PathRow(LocalizationManager.tr("settings.row.config_dir"), configDir.toString()));
        rows.add(new PathRow(LocalizationManager.tr("settings.row.config_global"),
                configDir.resolve("global.json").toString()));
        rows.add(new PathRow(LocalizationManager.tr("settings.row.config_local"),
                configDir.resolve("local-mode.json").toString()));
        rows.add(new PathRow(LocalizationManager.tr("settings.row.config_iloppis"),
                configDir.resolve("iloppis-mode.json").toString()));
        rows.add(new PathRow(LocalizationManager.tr("settings.row.logs_dir"), logsDir.toString()));
        rows.add(new PathRow(LocalizationManager.tr("settings.row.log_file"),
                logsDir.resolve("loppiskassan.log").toString()));
        rows.add(new PathRow(LocalizationManager.tr("settings.row.data_dir"), dataDir.toString()));
        rows.add(new PathRow(LocalizationManager.tr("settings.row.data_csv"),
                dataDir.resolve("loppiskassan.csv").toString()));
        rows.add(new PathRow(LocalizationManager.tr("settings.row.events_dir"), eventsDir.toString()));
        rows.add(new PathRow(LocalizationManager.tr("settings.row.event_dir"), eventDir.toString()));
        rows.add(new PathRow(LocalizationManager.tr("settings.row.local_metadata"),
                eventDir.resolve("local_metadata.json").toString()));
        rows.add(new PathRow(LocalizationManager.tr("settings.row.iloppis_metadata"),
                eventDir.resolve("iloppis_metadata.json").toString()));
        rows.add(new PathRow(LocalizationManager.tr("settings.row.pending_items"),
                eventDir.resolve("pending_items.jsonl").toString()));
        rows.add(new PathRow(LocalizationManager.tr("settings.row.uploaded_items"),
                eventDir.resolve("pending_items.jsonl").toString()));
        rows.add(new PathRow(LocalizationManager.tr("settings.row.rejected_items"),
                eventDir.resolve("rejected_purchases.jsonl").toString()));
        rows.add(new PathRow(LocalizationManager.tr("settings.row.sold_items"),
                eventDir.resolve("sold_items.jsonl").toString()));
        rows.add(new PathRow(LocalizationManager.tr("settings.row.archive_dir"),
                eventDir.resolve("archive").toString()));

        return rows;
    }

    private String buildHintText() {
        String eventId = AppModeManager.getEventId();
        if (eventId == null || eventId.isBlank()) {
            return LocalizationManager.tr("settings.dialog.hint.no_event");
        }
        return LocalizationManager.tr("settings.dialog.hint");
    }

    private void copySelectedPath() {
        int row = table.getSelectedRow();
        if (row < 0) {
            Popup.INFORMATION.showAndWait(
                    LocalizationManager.tr("settings.copy_missing.title"),
                    LocalizationManager.tr("settings.copy_missing.message"));
            return;
        }
        Object value = tableModel.getValueAt(row, 1);
        if (value == null) {
            return;
        }
        StringSelection selection = new StringSelection(value.toString());
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
        Popup.INFORMATION.showAndWait(
                LocalizationManager.tr("settings.copy_success.title"),
                LocalizationManager.tr("settings.copy_success.message"));
    }

    private String[] buildColumnNames() {
        return new String[]{
                LocalizationManager.tr("settings.table.label"),
                LocalizationManager.tr("settings.table.path")
        };
    }

    @Override
    public void reloadTexts() {
        setTitle(LocalizationManager.tr("settings.dialog.title"));
        subtitleLabel.setText(LocalizationManager.tr("settings.dialog.subtitle"));
        tableModel.setColumnIdentifiers(buildColumnNames());
        loadRows();
    }

    private record PathRow(String label, String path) {}
}
