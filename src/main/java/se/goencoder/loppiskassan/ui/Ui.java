package se.goencoder.loppiskassan.ui;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.border.CompoundBorder;
import javax.swing.BorderFactory;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.HierarchyEvent;

/**
 * Shared UI helper utilities and spacing constants.
 */
public final class Ui {
    public static final int SP_XS = 4;
    public static final int SP_S  = 8;
    public static final int SP_M  = 12;
    public static final int SP_L  = 16;
    public static final int SP_XL = 24;

    public static final Insets ROW = new Insets(SP_S, SP_L, SP_S, SP_L);
    public static Insets COMPACT_LABEL_RIGHT = new Insets(SP_S, SP_L, SP_S, SP_XS);
    public static Insets COMPACT_FIELD_LEFT  = new Insets(SP_S, SP_XS, SP_S, SP_L);

    private Ui() { }

    public static JPanel padded(JComponent c, int pad) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBorder(new EmptyBorder(pad, pad, pad, pad));
        p.add(c, BorderLayout.CENTER);
        return p;
    }

    public static JPanel card(String title) {
        JPanel p = new JPanel(new BorderLayout());
        TitledBorder tb = BorderFactory.createTitledBorder(title);
        CompoundBorder border = BorderFactory.createCompoundBorder(tb, new EmptyBorder(SP_L, SP_L, SP_L, SP_L));
        p.setBorder(border);
        p.putClientProperty("JComponent.outline", "list");
        return p;
    }

    public static GridBagConstraints gbc(int x, int y) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = x;
        gbc.gridy = y;
        gbc.insets = ROW;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = x == 1 ? 1.0 : 0.0;
        return gbc;
    }

    public static GridBagConstraints gbcCompactLabel(int x, int y) {
        GridBagConstraints gbc = gbc(x, y);
        gbc.insets = COMPACT_LABEL_RIGHT;
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.weightx = 0.0;
        return gbc;
    }

    public static GridBagConstraints gbcCompactField(int x, int y, double weightx) {
        GridBagConstraints gbc = gbc(x, y);
        gbc.insets = COMPACT_FIELD_LEFT;
        gbc.weightx = weightx;
        return gbc;
    }

    public static void makePrimary(JButton b) {
        b.putClientProperty("JButton.buttonType", "default");
        b.addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.PARENT_CHANGED) != 0) {
                JRootPane root = SwingUtilities.getRootPane(b);
                if (root != null && root.getDefaultButton() == null) {
                    root.setDefaultButton(b);
                }
            }
        });
    }

    public static void zebra(JTable t) {
        t.setRowHeight(28);
        t.putClientProperty("Table.alternateRowColor", Boolean.TRUE);
    }
}
