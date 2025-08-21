package se.goencoder.loppiskassan.ui;

import se.goencoder.loppiskassan.SoldItem;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

/**
 * Table model for sold items, with typed columns and no cell editing.
 */
public final class SoldItemsTableModel extends AbstractTableModel {
    /** Column indices (model indices) for safer cross-file use. */
    public static final int COLUMN_SELLER  = 0;
    public static final int COLUMN_PRICE   = 1;
    public static final int COLUMN_ITEM_ID = 2;

    private String[] columns;
    private final List<SoldItem> items = new ArrayList<>();

    public SoldItemsTableModel(String[] columnNames) {
        this.columns = columnNames;
    }

    public void setColumnNames(String[] columnNames) {
        this.columns = columnNames;
        fireTableStructureChanged();
    }

    @Override
    public int getRowCount() {
        return items.size();
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
            default -> String.class;
        };
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        SoldItem item = items.get(rowIndex);
        return switch (columnIndex) {
            case COLUMN_SELLER  -> item.getSeller();
            case COLUMN_PRICE   -> item.getPrice();
            case COLUMN_ITEM_ID -> item.getItemId();
            default             -> null;
        };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    /**
     * Insert item at the top.
     */
    public void addItem(SoldItem item) {
        items.add(0, item);
        fireTableRowsInserted(0, 0);
    }

    public void clear() {
        int size = items.size();
        if (size > 0) {
            items.clear();
            fireTableRowsDeleted(0, size - 1);
        }
    }

    public List<SoldItem> getItems() {
        return items;
    }
}
