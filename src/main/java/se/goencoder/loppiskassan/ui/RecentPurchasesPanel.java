package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.V1SoldItem;
import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.localization.LocalizationAware;
import se.goencoder.loppiskassan.localization.LocalizationManager.LanguageChangeListener;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.storage.JsonlHelper;
import se.goencoder.loppiskassan.storage.LocalEventPaths;
import se.goencoder.loppiskassan.util.RecentPurchases;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RecentPurchasesPanel extends JPanel implements LocalizationAware, SelectabableTab {
    private static final int MAX_PURCHASES = 10;
    private static final int MAX_TAIL_LINES = 250;
    private static final String CARD_TABLE = "table";
    private static final String CARD_EMPTY = "empty";

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel bodyPanel = new JPanel(cardLayout);
    private final RecentPurchasesTableModel tableModel = new RecentPurchasesTableModel();
    private final JTable purchasesTable = new JTable(tableModel);
    private final JLabel titleLabel = new JLabel();
    private final JLabel descriptionLabel = new JLabel();
    private final JLabel emptyLabel = new JLabel("", SwingConstants.CENTER);
    private final LanguageChangeListener languageChangeListener = this::reloadTexts;

    private List<RecentPurchases.PurchaseGroup> latestGroups = List.of();
    private Path lastLoadedPath;
    private long lastLoadedSize = -1L;
    private long lastLoadedModifiedMillis = -1L;

    public RecentPurchasesPanel() {
        setLayout(new BorderLayout());
        setBackground(AppColors.WHITE);
        LocalizationManager.addListener(languageChangeListener);

        add(createHeader(), BorderLayout.NORTH);
        add(createBody(), BorderLayout.CENTER);

        configureTable();
        reloadTexts();
    }

    private JPanel createHeader() {
        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setBackground(AppColors.WHITE);
        header.setBorder(BorderFactory.createEmptyBorder(32, 32, 16, 32));

        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 20f));
        titleLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(titleLabel);

        header.add(Box.createVerticalStrut(8));

        descriptionLabel.setFont(descriptionLabel.getFont().deriveFont(13f));
        descriptionLabel.setForeground(AppColors.TEXT_MUTED);
        descriptionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        header.add(descriptionLabel);

        return header;
    }

    private JPanel createBody() {
        bodyPanel.setBackground(AppColors.WHITE);
        bodyPanel.setBorder(BorderFactory.createEmptyBorder(0, 32, 32, 32));

        JScrollPane scrollPane = new JScrollPane(purchasesTable);
        scrollPane.getViewport().setBackground(AppColors.WHITE);
        scrollPane.setBorder(BorderFactory.createLineBorder(AppColors.BORDER, 1));
        bodyPanel.add(scrollPane, CARD_TABLE);

        JPanel emptyState = new JPanel(new BorderLayout());
        emptyState.setBackground(AppColors.WHITE);
        emptyState.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColors.BORDER, 1),
                BorderFactory.createEmptyBorder(24, 24, 24, 24)
        ));
        emptyLabel.setForeground(AppColors.TEXT_MUTED);
        emptyLabel.setFont(emptyLabel.getFont().deriveFont(14f));
        emptyState.add(emptyLabel, BorderLayout.CENTER);
        bodyPanel.add(emptyState, CARD_EMPTY);

        return bodyPanel;
    }

    private void configureTable() {
        purchasesTable.setRowHeight(28);
        purchasesTable.setFillsViewportHeight(true);
        purchasesTable.setFocusable(false);
        purchasesTable.setRowSelectionAllowed(false);
        purchasesTable.setCellSelectionEnabled(false);
        purchasesTable.setShowGrid(false);
        purchasesTable.setIntercellSpacing(new Dimension(0, 0));
        purchasesTable.getTableHeader().setReorderingAllowed(false);
        purchasesTable.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        refreshTableColumns();
    }

    private void refreshTableColumns() {
        PurchaseRowRenderer renderer = new PurchaseRowRenderer(tableModel);
        for (int i = 0; i < purchasesTable.getColumnModel().getColumnCount(); i++) {
            purchasesTable.getColumnModel().getColumn(i).setCellRenderer(renderer);
        }

        purchasesTable.getColumnModel().getColumn(0).setPreferredWidth(220);
        purchasesTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        purchasesTable.getColumnModel().getColumn(2).setPreferredWidth(140);
    }

    @Override
    public void selected() {
        loadPurchases();
    }

    @Override
    public void reloadTexts() {
        titleLabel.setText(LocalizationManager.tr("recent.title"));
        descriptionLabel.setText(LocalizationManager.tr("recent.description"));
        emptyLabel.setText(LocalizationManager.tr("recent.empty"));
        tableModel.setColumnNames(
                LocalizationManager.tr("recent.table.purchase"),
                LocalizationManager.tr("recent.table.seller"),
                LocalizationManager.tr("recent.table.price"));
        refreshTableColumns();
        refreshRowsFromCache();
    }

    @Override
    public void removeNotify() {
        LocalizationManager.removeListener(languageChangeListener);
        super.removeNotify();
    }

    private void loadPurchases() {
        String eventId = AppModeManager.getEventId();
        if (eventId == null || eventId.isBlank()) {
            latestGroups = List.of();
            lastLoadedPath = null;
            lastLoadedSize = -1L;
            lastLoadedModifiedMillis = -1L;
            refreshRowsFromCache();
            return;
        }

        Path pendingPath = LocalEventPaths.getPendingItemsPath(eventId);
        try {
            long fileSize = Files.exists(pendingPath) ? Files.size(pendingPath) : -1L;
            long modifiedMillis = Files.exists(pendingPath) ? Files.getLastModifiedTime(pendingPath).toMillis() : -1L;
            if (pendingPath.equals(lastLoadedPath)
                    && fileSize == lastLoadedSize
                    && modifiedMillis == lastLoadedModifiedMillis) {
                refreshRowsFromCache();
                return;
            }

            List<V1SoldItem> items = JsonlHelper.readLastItems(pendingPath, MAX_TAIL_LINES);
            latestGroups = RecentPurchases.latest(items, MAX_PURCHASES);
            lastLoadedPath = pendingPath;
            lastLoadedSize = fileSize;
            lastLoadedModifiedMillis = modifiedMillis;
            refreshRowsFromCache();
        } catch (Exception e) {
            latestGroups = List.of();
            refreshRowsFromCache();
            Popup.ERROR.showAndWait(
                    LocalizationManager.tr("error.generic.title"),
                    LocalizationManager.tr("error.read_register_file", pendingPath));
        }
    }

    private void refreshRowsFromCache() {
        List<RecentPurchaseRow> rows = buildRows(latestGroups);
        tableModel.setRows(rows);
        cardLayout.show(bodyPanel, rows.isEmpty() ? CARD_EMPTY : CARD_TABLE);
    }

    private List<RecentPurchaseRow> buildRows(List<RecentPurchases.PurchaseGroup> groups) {
        List<RecentPurchaseRow> rows = new ArrayList<>();
        NumberFormat numberFormat = NumberFormat.getIntegerInstance(new Locale(LocalizationManager.getLanguage()));
        DateTimeFormatter timeFormat = DateTimeFormatter.ofPattern("HH:mm");

        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            RecentPurchases.PurchaseGroup group = groups.get(groupIndex);
            String timeText = formatPurchaseTime(group.soldTime(), timeFormat);
            rows.add(RecentPurchaseRow.purchase(groupIndex, LocalizationManager.tr("recent.purchase_at", timeText), "", ""));

            for (V1SoldItem item : group.items()) {
                rows.add(RecentPurchaseRow.item(
                        groupIndex,
                        "",
                        String.valueOf(item.getSeller()),
                        numberFormat.format(item.getPrice())));
            }

            rows.add(RecentPurchaseRow.summary(
                    groupIndex,
                    "",
                    LocalizationManager.tr("recent.total"),
                    numberFormat.format(group.totalAmount())));
        }
        return rows;
    }

    private String formatPurchaseTime(LocalDateTime soldTime, DateTimeFormatter formatter) {
        if (soldTime == null) {
            return "—";
        }
        return soldTime.toLocalTime().format(formatter);
    }

    private enum RowType {
        PURCHASE,
        ITEM,
        SUMMARY
    }

    private record RecentPurchaseRow(int groupIndex, RowType rowType, String purchase, String seller, String price) {
        private static RecentPurchaseRow purchase(int groupIndex, String purchase, String seller, String price) {
            return new RecentPurchaseRow(groupIndex, RowType.PURCHASE, purchase, seller, price);
        }

        private static RecentPurchaseRow item(int groupIndex, String purchase, String seller, String price) {
            return new RecentPurchaseRow(groupIndex, RowType.ITEM, purchase, seller, price);
        }

        private static RecentPurchaseRow summary(int groupIndex, String purchase, String seller, String price) {
            return new RecentPurchaseRow(groupIndex, RowType.SUMMARY, purchase, seller, price);
        }
    }

    private static final class RecentPurchasesTableModel extends AbstractTableModel {
        private final List<RecentPurchaseRow> rows = new ArrayList<>();
        private String[] columnNames = {"", "", ""};

        private void setColumnNames(String purchase, String seller, String price) {
            columnNames = new String[]{purchase, seller, price};
            fireTableStructureChanged();
        }

        private void setRows(List<RecentPurchaseRow> newRows) {
            rows.clear();
            rows.addAll(newRows);
            fireTableDataChanged();
        }

        private RecentPurchaseRow rowAt(int rowIndex) {
            return rows.get(rowIndex);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columnNames.length;
        }

        @Override
        public String getColumnName(int column) {
            return columnNames[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            RecentPurchaseRow row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.purchase();
                case 1 -> row.seller();
                case 2 -> row.price();
                default -> "";
            };
        }
    }

    private static final class PurchaseRowRenderer extends DefaultTableCellRenderer {
        private static final Color[] GROUP_COLORS = {
                AppColors.SELECTED_BG,
                AppColors.FIELD_BG,
                AppColors.STATE_OPEN_BG,
                AppColors.STATE_PROCESSING_BG
        };

        private final RecentPurchasesTableModel model;

        private PurchaseRowRenderer(RecentPurchasesTableModel model) {
            this.model = model;
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, false, false, row, column);

            RecentPurchaseRow dataRow = model.rowAt(row);
            setBackground(GROUP_COLORS[dataRow.groupIndex() % GROUP_COLORS.length]);
            setForeground(dataRow.rowType() == RowType.PURCHASE ? AppColors.TEXT_SECONDARY : AppColors.TEXT_PRIMARY);
            setHorizontalAlignment(column == 2 ? SwingConstants.RIGHT : SwingConstants.LEFT);

            Font baseFont = table.getFont().deriveFont(Font.PLAIN, 13f);
            if (dataRow.rowType() == RowType.SUMMARY || (dataRow.rowType() == RowType.PURCHASE && column == 0)) {
                setFont(baseFont.deriveFont(Font.BOLD));
            } else {
                setFont(baseFont);
            }

            return this;
        }
    }
}
