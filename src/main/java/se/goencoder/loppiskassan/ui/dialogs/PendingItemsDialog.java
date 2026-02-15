package se.goencoder.loppiskassan.ui.dialogs;

import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.storage.PendingItemsStore;
import se.goencoder.loppiskassan.ui.AppButton;
import se.goencoder.loppiskassan.ui.AppColors;
import se.goencoder.loppiskassan.ui.Renderers;
import se.goencoder.loppiskassan.util.SwedishDateFormatter;

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
import java.util.ArrayList;
import java.util.List;

public class PendingItemsDialog extends JDialog implements LocalizationAware {
    private final String eventId;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel emptyLabel;

    public static void show(Component parent, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            se.goencoder.loppiskassan.ui.Popup.ERROR.showAndWait(
                    LocalizationManager.tr("error.no_event_selected.title"),
                    LocalizationManager.tr("error.no_event_selected.message"));
            return;
        }
        PendingItemsDialog dialog = new PendingItemsDialog(parent, eventId);
        dialog.setVisible(true);
    }

    private PendingItemsDialog(Component parent, String eventId) {
        super(SwingUtilities.getWindowAncestor(parent));
        this.eventId = eventId;

        setModal(true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(LocalizationManager.tr("pending.dialog.title"));
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColors.WHITE);

        JLabel titleLabel = new JLabel(LocalizationManager.tr("pending.dialog.title"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(16f));
        titleLabel.setForeground(AppColors.TEXT_PRIMARY);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppColors.WHITE);
        header.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        header.add(titleLabel, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        String[] columns = buildColumnNames();
        tableModel = new DefaultTableModel(null, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(26);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.putClientProperty("Table.alternateRowColor", Boolean.TRUE);

        configureTableColumns();

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(AppColors.WHITE);
        scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, AppColors.BORDER));

        emptyLabel = new JLabel(LocalizationManager.tr("pending.dialog.empty"));
        emptyLabel.setForeground(AppColors.TEXT_MUTED);
        emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);
        emptyLabel.setBorder(BorderFactory.createEmptyBorder(24, 16, 24, 16));

        JPanel content = new JPanel(new BorderLayout());
        content.setBackground(AppColors.WHITE);
        content.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
        content.add(scrollPane, BorderLayout.CENTER);
        content.add(emptyLabel, BorderLayout.SOUTH);
        add(content, BorderLayout.CENTER);

        JButton closeButton = AppButton.create(LocalizationManager.tr("button.close"),
                AppButton.Variant.SECONDARY, AppButton.Size.MEDIUM);
        closeButton.addActionListener(evt -> dispose());

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 12));
        footer.setBackground(AppColors.WHITE);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));
        footer.add(closeButton);
        add(footer, BorderLayout.SOUTH);

        loadData();

        setSize(new Dimension(760, 420));
        setLocationRelativeTo(parent);
        LocalizationManager.addListener(this::reloadTexts);
    }

    private void loadData() {
        List<V1SoldItem> pendingItems = new ArrayList<>();
        if (eventId != null && !eventId.isBlank()) {
            try {
                pendingItems = new PendingItemsStore(eventId).readPending();
            } catch (Exception ignored) {
                pendingItems = new ArrayList<>();
            }
        }

        tableModel.setRowCount(0);
        for (V1SoldItem item : pendingItems) {
            tableModel.addRow(new Object[]{
                    SwedishDateFormatter.formatDateWithTime(item.getSoldTime()),
                    item.getSeller(),
                    item.getPrice(),
                    item.getPaymentMethod() == se.goencoder.loppiskassan.V1PaymentMethod.Kontant
                            ? LocalizationManager.tr("payment.cash")
                            : LocalizationManager.tr("payment.swish"),
                    item.getItemId()
            });
        }

        emptyLabel.setVisible(pendingItems.isEmpty());
    }

    private String[] buildColumnNames() {
        return new String[]{
                LocalizationManager.tr("pending.table.time"),
                LocalizationManager.tr("pending.table.seller"),
                LocalizationManager.tr("pending.table.price"),
                LocalizationManager.tr("pending.table.payment"),
                LocalizationManager.tr("pending.table.item_id")
        };
    }

    @Override
    public void reloadTexts() {
        setTitle(LocalizationManager.tr("pending.dialog.title"));
        tableModel.setColumnIdentifiers(buildColumnNames());
        emptyLabel.setText(LocalizationManager.tr("pending.dialog.empty"));
        configureTableColumns();
    }

    private void configureTableColumns() {
        if (table.getColumnModel().getColumnCount() < 5) {
            return;
        }
        DefaultTableCellRenderer rightAligned = (DefaultTableCellRenderer) Renderers.rightAligned();
        table.getColumnModel().getColumn(2).setCellRenderer(rightAligned);

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(0).setCellRenderer(center);

        int[] widths = {140, 70, 90, 110, 220};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }
}
