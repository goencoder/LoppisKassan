package se.goencoder.loppiskassan.ui.dialogs;

import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.service.BackgroundSyncManager;
import se.goencoder.loppiskassan.service.RejectedItemsManager;
import se.goencoder.loppiskassan.storage.RejectedItemEntry;
import se.goencoder.loppiskassan.storage.RejectedItemsStore;
import se.goencoder.loppiskassan.ui.AppButton;
import se.goencoder.loppiskassan.ui.AppColors;
import se.goencoder.loppiskassan.ui.Popup;
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
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class RejectedItemsDialog extends JDialog implements LocalizationAware {
    private static final int COLUMN_TIME = 0;
    private static final int COLUMN_SELLER = 1;
    private static final int COLUMN_PRICE = 2;
    private static final int COLUMN_PAYMENT = 3;
    private static final int COLUMN_ITEM_ID = 4;
    private static final int COLUMN_REASON = 5;
    private static final int COLUMN_EDIT = 6;
    private static final int COLUMN_DELETE = 7;

    private final String eventId;
    private final RejectedItemsStore store;
    private final RejectedItemsTableModel tableModel;
    private final JTable table;
    private final JLabel emptyLabel;

    public static void show(Component parent, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("error.no_event_selected.title"),
                    LocalizationManager.tr("error.no_event_selected.message"));
            return;
        }
        RejectedItemsDialog dialog = new RejectedItemsDialog(parent, eventId);
        dialog.setVisible(true);
    }

    private RejectedItemsDialog(Component parent, String eventId) {
        super(SwingUtilities.getWindowAncestor(parent));
        this.eventId = eventId;
        this.store = new RejectedItemsStore(eventId);

        setModal(true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(LocalizationManager.tr("rejected.dialog.title"));
        setLayout(new BorderLayout());
        getContentPane().setBackground(AppColors.WHITE);

        JLabel titleLabel = new JLabel(LocalizationManager.tr("rejected.dialog.title"));
        titleLabel.setFont(titleLabel.getFont().deriveFont(16f));
        titleLabel.setForeground(AppColors.TEXT_PRIMARY);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppColors.WHITE);
        header.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        header.add(titleLabel, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        tableModel = new RejectedItemsTableModel(buildColumnNames());
        table = new JTable(tableModel);
        table.setRowHeight(28);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.putClientProperty("Table.alternateRowColor", Boolean.TRUE);

        configureTableColumns();

        table.setRowSorter(new TableRowSorter<>(tableModel));
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewRow = table.rowAtPoint(e.getPoint());
                int viewColumn = table.columnAtPoint(e.getPoint());
                if (viewRow < 0 || viewColumn < 0) {
                    return;
                }
                int modelRow = table.convertRowIndexToModel(viewRow);
                if (viewColumn == COLUMN_EDIT) {
                    editEntry(modelRow);
                } else if (viewColumn == COLUMN_DELETE) {
                    deleteEntry(modelRow);
                }
            }
        });

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(AppColors.WHITE);
        scrollPane.setBorder(BorderFactory.createMatteBorder(1, 0, 1, 0, AppColors.BORDER));

        emptyLabel = new JLabel(LocalizationManager.tr("rejected.dialog.empty"));
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

        setSize(new Dimension(920, 460));
        setLocationRelativeTo(parent);
        LocalizationManager.addListener(this::reloadTexts);
    }

    private void loadData() {
        List<RejectedItemEntry> entries = new ArrayList<>();
        if (eventId != null && !eventId.isBlank()) {
            try {
                entries = store.readAll();
            } catch (IOException ignored) {
                entries = new ArrayList<>();
            }
        }
        tableModel.setEntries(entries);
        emptyLabel.setVisible(entries.isEmpty());
    }

    private void editEntry(int modelRow) {
        RejectedItemEntry entry = tableModel.getEntry(modelRow);
        if (entry == null) {
            return;
        }
        if (!entry.canRequeue()) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("rejected.edit.missing_fields.title"),
                    LocalizationManager.tr("rejected.edit.missing_fields.message")
            );
            return;
        }

        Integer newSeller = RejectedItemEditDialog.show(this, entry);
        if (newSeller == null) {
            return;
        }

        RejectedItemEntry updated = new RejectedItemEntry(
                entry.getItemId(),
                entry.getPurchaseId(),
                newSeller,
                entry.getPrice(),
                entry.getPaymentMethod(),
                entry.getSoldTime(),
                entry.getErrorCode(),
                entry.getReason(),
                entry.getTimestamp()
        );

        V1SoldItem requeue = updated.toSoldItem();
        if (requeue == null) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("rejected.edit.missing_fields.title"),
                    LocalizationManager.tr("rejected.edit.missing_fields.message")
            );
            return;
        }

        try {
            BackgroundSyncManager.getInstance().upsertPendingItem(eventId, requeue);

            tableModel.removeEntry(modelRow);
            store.saveAll(tableModel.getEntries());
            BackgroundSyncManager.getInstance().notifyPendingCountChanged();
            RejectedItemsManager.getInstance().notifyRejectedCountChanged(eventId);
            BackgroundSyncManager.getInstance().triggerSyncNow();
            emptyLabel.setVisible(tableModel.getRowCount() == 0);
        } catch (IOException e) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("error.save_file",
                            se.goencoder.loppiskassan.storage.LocalEventPaths.getPendingItemsPath(eventId)),
                    e.getMessage());
        }
    }

    private void deleteEntry(int modelRow) {
        RejectedItemEntry entry = tableModel.getEntry(modelRow);
        if (entry == null) {
            return;
        }
        boolean confirmed = Popup.CONFIRM.showConfirmDialog(
                LocalizationManager.tr("rejected.delete.confirm.title"),
                LocalizationManager.tr("rejected.delete.confirm.message")
        );
        if (!confirmed) {
            return;
        }

        try {
            tableModel.removeEntry(modelRow);
            store.saveAll(tableModel.getEntries());
            RejectedItemsManager.getInstance().notifyRejectedCountChanged(eventId);
            emptyLabel.setVisible(tableModel.getRowCount() == 0);
        } catch (IOException e) {
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("error.save_file",
                            se.goencoder.loppiskassan.storage.LocalEventPaths.getRejectedPurchasesPath(eventId)),
                    e.getMessage());
        }
    }

    private String[] buildColumnNames() {
        return new String[]{
                LocalizationManager.tr("rejected.table.time"),
                LocalizationManager.tr("rejected.table.seller"),
                LocalizationManager.tr("rejected.table.price"),
                LocalizationManager.tr("rejected.table.payment"),
                LocalizationManager.tr("rejected.table.item_id"),
                LocalizationManager.tr("rejected.table.reason"),
                LocalizationManager.tr("rejected.table.edit"),
                LocalizationManager.tr("rejected.table.delete")
        };
    }

    @Override
    public void reloadTexts() {
        setTitle(LocalizationManager.tr("rejected.dialog.title"));
        tableModel.setColumnNames(buildColumnNames());
        emptyLabel.setText(LocalizationManager.tr("rejected.dialog.empty"));
        configureTableColumns();
    }

    private void configureTableColumns() {
        if (table.getColumnModel().getColumnCount() < 8) {
            return;
        }
        DefaultTableCellRenderer rightAligned = (DefaultTableCellRenderer) Renderers.rightAligned();
        table.getColumnModel().getColumn(COLUMN_PRICE).setCellRenderer(rightAligned);

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        table.getColumnModel().getColumn(COLUMN_TIME).setCellRenderer(center);
        table.getColumnModel().getColumn(COLUMN_EDIT).setCellRenderer(Renderers.editButton());
        table.getColumnModel().getColumn(COLUMN_DELETE).setCellRenderer(Renderers.deleteButton());

        int[] widths = {140, 70, 90, 110, 220, 260, 70, 70};
        for (int i = 0; i < widths.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        table.getColumnModel().getColumn(COLUMN_EDIT).setMaxWidth(70);
        table.getColumnModel().getColumn(COLUMN_DELETE).setMaxWidth(70);
    }

    private static final class RejectedItemsTableModel extends AbstractTableModel {
        private String[] columns;
        private List<RejectedItemEntry> entries = new ArrayList<>();

        private RejectedItemsTableModel(String[] columns) {
            this.columns = columns;
        }

        private void setColumnNames(String[] columns) {
            this.columns = columns;
            fireTableStructureChanged();
        }

        private void setEntries(List<RejectedItemEntry> entries) {
            this.entries = new ArrayList<>(entries);
            fireTableDataChanged();
        }

        private List<RejectedItemEntry> getEntries() {
            return entries;
        }

        private RejectedItemEntry getEntry(int row) {
            if (row < 0 || row >= entries.size()) {
                return null;
            }
            return entries.get(row);
        }

        private void removeEntry(int row) {
            if (row < 0 || row >= entries.size()) {
                return;
            }
            entries.remove(row);
            fireTableRowsDeleted(row, row);
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return switch (columnIndex) {
                case COLUMN_SELLER, COLUMN_PRICE -> Integer.class;
                case COLUMN_EDIT, COLUMN_DELETE -> String.class;
                default -> String.class;
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            RejectedItemEntry entry = entries.get(rowIndex);
            return switch (columnIndex) {
                case COLUMN_TIME -> entry.getSoldTime() == null
                        ? ""
                        : SwedishDateFormatter.formatDateWithTime(entry.getSoldTime());
                case COLUMN_SELLER -> entry.getSeller();
                case COLUMN_PRICE -> entry.getPrice();
                case COLUMN_PAYMENT -> entry.getPaymentMethod() == null
                        ? ""
                        : (entry.getPaymentMethod() == se.goencoder.loppiskassan.V1PaymentMethod.Kontant
                            ? LocalizationManager.tr("payment.cash")
                            : LocalizationManager.tr("payment.swish"));
                case COLUMN_ITEM_ID -> entry.getItemId();
                case COLUMN_REASON -> entry.getReason() == null ? "" : entry.getReason();
                case COLUMN_EDIT -> "✎";
                case COLUMN_DELETE -> "✕";
                default -> "";
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }
}
