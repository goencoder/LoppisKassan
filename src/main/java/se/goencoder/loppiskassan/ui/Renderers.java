package se.goencoder.loppiskassan.ui;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

/**
 * Common table cell renderers.
 */
public final class Renderers {
    private Renderers() {}

    /**
     * Renderer that right aligns cell contents.
     *
     * @return table cell renderer with right alignment
     */
    public static TableCellRenderer rightAligned() {
        DefaultTableCellRenderer r = new DefaultTableCellRenderer();
        r.setHorizontalAlignment(SwingConstants.RIGHT);
        return r;
    }
    
    /**
     * Renderer for delete button column with red X icon.
     * 
     * @return table cell renderer that displays a red X button
     */
    public static TableCellRenderer deleteButton() {
        return new DefaultTableCellRenderer() {
            private final JButton button = new JButton("✕");
            
            {
                button.setForeground(AppColors.DANGER);
                button.setBackground(AppColors.WHITE);
                button.setBorder(new RoundedBorder(new Color(0, 0, 0, 0), 0, 6, new Insets(4, 8, 4, 8)));
                button.setFocusPainted(false);
                button.setCursor(new Cursor(Cursor.HAND_CURSOR));
                button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));
                button.setOpaque(true);
            }
            
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                if (isSelected) {
                    button.setBackground(table.getSelectionBackground());
                } else {
                    button.setBackground(table.getBackground());
                }
                return button;
            }
        };
    }

    /**
     * Renderer for edit button column with pencil icon.
     *
     * @return table cell renderer that displays an edit button
     */
    public static TableCellRenderer editButton() {
        return new DefaultTableCellRenderer() {
            private final JButton button = new JButton("✎");

            {
                button.setForeground(AppColors.ACCENT);
                button.setBackground(AppColors.WHITE);
                button.setBorder(new RoundedBorder(new Color(0, 0, 0, 0), 0, 6, new Insets(4, 8, 4, 8)));
                button.setFocusPainted(false);
                button.setCursor(new Cursor(Cursor.HAND_CURSOR));
                button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));
                button.setOpaque(true);
            }

            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                if (isSelected) {
                    button.setBackground(table.getSelectionBackground());
                } else {
                    button.setBackground(table.getBackground());
                }
                return button;
            }
        };
    }
}
