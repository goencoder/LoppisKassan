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
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

public class RejectedItemEditDialog extends JDialog {
    private Integer resultSeller;

    public static Integer show(Component parent, RejectedItemEntry entry) {
        RejectedItemEditDialog dialog = new RejectedItemEditDialog(parent, entry);
        dialog.setVisible(true);
        return dialog.resultSeller;
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
                LocalizationManager.tr("rejected.edit.price"), valueLabel(entry.getPrice() == null ? "" : entry.getPrice() + ""));
        addFormRow(form, label, field, 3,
                LocalizationManager.tr("rejected.edit.payment"), valueLabel(entry.getPaymentMethod() == null ? "" :
                        (entry.getPaymentMethod() == se.goencoder.loppiskassan.V1PaymentMethod.Kontant
                                ? LocalizationManager.tr("payment.cash")
                                : LocalizationManager.tr("payment.swish"))));
        addFormRow(form, label, field, 4,
                LocalizationManager.tr("rejected.edit.sold_time"),
                valueLabel(entry.getSoldTime() == null ? "" : SwedishDateFormatter.formatDateWithTime(entry.getSoldTime())));
        addFormRow(form, label, field, 5,
                LocalizationManager.tr("rejected.edit.reason"), valueLabel(entry.getReason()));

        add(form, BorderLayout.CENTER);

        JButton cancel = AppButton.create(LocalizationManager.tr("button.cancel"),
                AppButton.Variant.SECONDARY, AppButton.Size.MEDIUM);
        cancel.addActionListener(evt -> dispose());

        JButton save = AppButton.create(LocalizationManager.tr("button.save"),
                AppButton.Variant.PRIMARY, AppButton.Size.MEDIUM);
        save.addActionListener(evt -> {
            String text = sellerField.getText().trim();
            try {
                int seller = Integer.parseInt(text);
                if (seller <= 0) {
                    throw new NumberFormatException();
                }
                resultSeller = seller;
                dispose();
            } catch (NumberFormatException ex) {
                Popup.ERROR.showAndWait(
                        LocalizationManager.tr("cashier.invalid_seller.title"),
                        LocalizationManager.tr("cashier.invalid_seller.message")
                );
            }
        });

        JPanel footer = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 12));
        footer.setBackground(AppColors.WHITE);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 16, 8, 16));
        footer.add(cancel);
        footer.add(save);
        add(footer, BorderLayout.SOUTH);

        setSize(520, 420);
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
}
