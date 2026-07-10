package se.goencoder.loppiskassan.ui.dialogs;

import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.storage.RejectedItemEntry;
import se.goencoder.loppiskassan.ui.AppButton;
import se.goencoder.loppiskassan.ui.AppColors;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.util.SwedishDateFormatter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public class RejectedItemEditDialog extends JDialog {

    public record EditResult(int seller, int price) {}

    private EditResult result;

    public static EditResult show(Component parent, RejectedItemEntry entry) {
        RejectedItemEditDialog dialog = new RejectedItemEditDialog(parent, entry);
        dialog.setVisible(true);
        return dialog.result;
    }

    private RejectedItemEditDialog(Component parent, RejectedItemEntry entry) {
        super(SwingUtilities.getWindowAncestor(parent));
        setModal(true);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setTitle(LocalizationManager.tr("rejected.edit.title"));
        getContentPane().setBackground(AppColors.WHITE);
        setLayout(new BorderLayout());

        JLabel title = new JLabel(LocalizationManager.tr("rejected.edit.title"));
        title.setFont(title.getFont().deriveFont(16f));
        title.setForeground(AppColors.TEXT_PRIMARY);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(AppColors.WHITE);
        header.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
        header.add(title, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);

        JTextField sellerField = new JTextField(entry.getSeller() == null ? "" : String.valueOf(entry.getSeller()));
        sellerField.setBackground(AppColors.FIELD_BG);
        sellerField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColors.BORDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

        JTextField priceField = new JTextField(entry.getPrice() == null ? "" : String.valueOf(entry.getPrice()));
        priceField.setBackground(AppColors.FIELD_BG);
        priceField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppColors.BORDER),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(AppColors.WHITE);
        form.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

        GridBagConstraints label = new GridBagConstraints();
        label.anchor = GridBagConstraints.LINE_END;
        label.insets = new Insets(6, 0, 6, 12);
        label.gridx = 0;

        GridBagConstraints field = new GridBagConstraints();
        field.gridx = 1;
        field.weightx = 1.0;
        field.fill = GridBagConstraints.HORIZONTAL;
        field.insets = new Insets(6, 0, 6, 0);

        addFormRow(form, label, field, 0,
                LocalizationManager.tr("rejected.edit.seller"), sellerField);
        addFormRow(form, label, field, 1,
                LocalizationManager.tr("rejected.edit.item_id"), valueLabel(entry.getItemId()));
        addFormRow(form, label, field, 2,
                LocalizationManager.tr("rejected.edit.price"), priceField);
        addFormRow(form, label, field, 3,
                LocalizationManager.tr("rejected.edit.payment"), valueLabel(entry.getPaymentMethod() == null ? "" :
                        (entry.getPaymentMethod() == se.goencoder.loppiskassan.V1PaymentMethod.Kontant
                                ? LocalizationManager.tr("payment.cash")
                                : LocalizationManager.tr("payment.swish"))));
        addFormRow(form, label, field, 4,
                LocalizationManager.tr("rejected.edit.sold_time"),
                valueLabel(entry.getSoldTime() == null ? "" : SwedishDateFormatter.formatDateWithTime(entry.getSoldTime())));
        addFormRow(form, label, field, 5,
                LocalizationManager.tr("rejected.edit.reason"), reasonArea(entry.getReason()));

        add(form, BorderLayout.CENTER);

        JButton cancel = AppButton.create(LocalizationManager.tr("button.cancel"),
                AppButton.Variant.SECONDARY, AppButton.Size.MEDIUM);
        cancel.addActionListener(evt -> dispose());

        JButton save = AppButton.create(LocalizationManager.tr("button.save"),
                AppButton.Variant.PRIMARY, AppButton.Size.MEDIUM);
        save.addActionListener(evt -> {
            int seller;
            int price;
            try {
                seller = Integer.parseInt(sellerField.getText().trim());
                if (seller <= 0) throw new NumberFormatException("seller");
            } catch (NumberFormatException ex) {
                Popup.ERROR.showAndWait(
                        LocalizationManager.tr("cashier.invalid_seller.title"),
                        LocalizationManager.tr("cashier.invalid_seller.message")
                );
                return;
            }
            try {
                price = Integer.parseInt(priceField.getText().trim());
                if (price <= 0) throw new NumberFormatException("price");
            } catch (NumberFormatException ex) {
                Popup.ERROR.showAndWait(
                        LocalizationManager.tr("cashier.invalid_price.title"),
                        LocalizationManager.tr("cashier.invalid_price.message")
                );
                return;
            }
            result = new EditResult(seller, price);
            dispose();
        });

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 12));
        footer.setBackground(AppColors.WHITE);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));
        footer.add(cancel);
        footer.add(save);
        add(footer, BorderLayout.SOUTH);

        setSize(560, 500);
        setLocationRelativeTo(parent);
    }

    private void addFormRow(JPanel panel,
                            GridBagConstraints labelConstraints,
                            GridBagConstraints fieldConstraints,
                            int row,
                            String labelText,
                            Component field) {
        GridBagConstraints lbl = (GridBagConstraints) labelConstraints.clone();
        lbl.gridy = row;
        panel.add(new JLabel(labelText), lbl);

        GridBagConstraints fld = (GridBagConstraints) fieldConstraints.clone();
        fld.gridy = row;
        panel.add(field, fld);
    }

    private JLabel valueLabel(String value) {
        JLabel label = new JLabel(value == null ? "" : value);
        label.setForeground(AppColors.TEXT_SECONDARY);
        label.setHorizontalAlignment(SwingConstants.LEFT);
        return label;
    }

    private JScrollPane reasonArea(String value) {
        JTextArea area = new JTextArea(value == null ? "" : value);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setForeground(AppColors.TEXT_SECONDARY);
        area.setBackground(AppColors.FIELD_BG);
        area.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        area.setFont(new JLabel().getFont());
        JScrollPane scroll = new JScrollPane(area,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createLineBorder(AppColors.BORDER));
        scroll.setPreferredSize(new Dimension(0, 72));
        return scroll;
    }
}
