package se.goencoder.loppiskassan.ui.dialogs;

import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.ui.AppButton;
import se.goencoder.loppiskassan.ui.AppColors;
import se.goencoder.loppiskassan.ui.TextFilters;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Modal dialog for requesting a cashier code with an optional "remember" choice.
 */
public class CashierCodeDialog extends JDialog {

    public static final class Result {
        private final String code;
        private final boolean remember;

        public Result(String code, boolean remember) {
            this.code = code;
            this.remember = remember;
        }

        public String getCode() {
            return code;
        }

        public boolean isRemember() {
            return remember;
        }
    }

    private Result result;
    private JTextField codeField;
    private JCheckBox rememberCheckbox;
    private JButton confirmButton;

    private CashierCodeDialog(Frame owner, String title, String message, boolean rememberDefault) {
        super(owner, title, true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        buildUI(message, rememberDefault);
        pack();
        setLocationRelativeTo(owner);
    }

    /**
     * Show the dialog and return the entered result or null if cancelled.
     */
    public Result showDialog() {
        setVisible(true);
        return result;
    }

    public static Result showDialog(Component parent, String title, String message, boolean rememberDefault) {
        Frame owner = resolveOwner(parent);
        CashierCodeDialog dialog = new CashierCodeDialog(owner, title, message, rememberDefault);
        return dialog.showDialog();
    }

    private static Frame resolveOwner(Component parent) {
        if (parent != null) {
            Window window = SwingUtilities.getWindowAncestor(parent);
            if (window instanceof Frame frame) {
                return frame;
            }
        }
        for (Frame frame : Frame.getFrames()) {
            if (frame.isDisplayable()) {
                return frame;
            }
        }
        return null;
    }

    private void buildUI(String message, boolean rememberDefault) {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(20, 24, 20, 24));
        root.setBackground(AppColors.WHITE);

        JLabel messageLabel = new JLabel("<html><div style='width:320px'>" + message + "</div></html>");
        messageLabel.setFont(messageLabel.getFont().deriveFont(Font.PLAIN, 13f));
        messageLabel.setForeground(AppColors.TEXT_SECONDARY);
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel codeLabel = new JLabel(LocalizationManager.tr("cashier_code.dialog.label"));
        codeLabel.setFont(codeLabel.getFont().deriveFont(Font.BOLD, 12f));
        codeLabel.setForeground(AppColors.TEXT_PRIMARY);
        codeLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

        codeField = new JTextField(16);
        codeField.setBackground(AppColors.FIELD_BG);
        codeField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        TextFilters.install(codeField, new TextFilters.AlnumDashUpperFilter(16));
        codeField.setAlignmentX(Component.LEFT_ALIGNMENT);

        rememberCheckbox = new JCheckBox(LocalizationManager.tr("cashier_code.dialog.remember"));
        rememberCheckbox.setSelected(rememberDefault);
        rememberCheckbox.setOpaque(false);
        rememberCheckbox.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonsPanel.setOpaque(false);

        JButton cancelButton = new JButton(LocalizationManager.tr("cashier_code.dialog.cancel"));
        AppButton.applyStyle(cancelButton, AppButton.Variant.SECONDARY, AppButton.Size.MEDIUM);
        cancelButton.addActionListener(e -> dispose());

        confirmButton = new JButton(LocalizationManager.tr("cashier_code.dialog.confirm"));
        AppButton.applyStyle(confirmButton, AppButton.Variant.PRIMARY, AppButton.Size.MEDIUM);
        confirmButton.addActionListener(e -> {
            result = new Result(codeField.getText().trim(), rememberCheckbox.isSelected());
            dispose();
        });

        buttonsPanel.add(cancelButton);
        buttonsPanel.add(confirmButton);

        codeField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { updateConfirmState(); }
            public void removeUpdate(DocumentEvent e) { updateConfirmState(); }
            public void changedUpdate(DocumentEvent e) { updateConfirmState(); }
        });

        root.add(messageLabel);
        root.add(Box.createVerticalStrut(16));
        root.add(codeLabel);
        root.add(Box.createVerticalStrut(6));
        root.add(codeField);
        root.add(Box.createVerticalStrut(10));
        root.add(rememberCheckbox);
        root.add(Box.createVerticalStrut(18));
        root.add(buttonsPanel);

        setContentPane(root);
        getRootPane().setDefaultButton(confirmButton);
        updateConfirmState();
    }

    private void updateConfirmState() {
        if (confirmButton != null && codeField != null) {
            confirmButton.setEnabled(!codeField.getText().trim().isEmpty());
        }
    }
}
