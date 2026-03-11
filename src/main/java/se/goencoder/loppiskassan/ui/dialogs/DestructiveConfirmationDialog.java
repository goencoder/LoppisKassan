package se.goencoder.loppiskassan.ui.dialogs;

import se.goencoder.loppiskassan.ui.AppButton;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

/**
 * Confirmation dialog requiring user to type a specific word (e.g., "RADERA") to confirm.
 * Used for destructive actions like clearing all sales data.
 */
public class DestructiveConfirmationDialog extends JDialog {
    private final String requiredText;
    private boolean confirmed = false;

    public DestructiveConfirmationDialog(Frame owner, String title, String message, String requiredText) {
        super(owner, title, true);
        this.requiredText = requiredText;
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        buildUI(message);
        pack();
        setLocationRelativeTo(owner);
    }

    private void buildUI(String message) {
        JPanel root = new JPanel(new BorderLayout(0, 16));
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JLabel messageLabel = new JLabel("<html><div style='width:300px'>" + message + "</div></html>");
        messageLabel.setFont(messageLabel.getFont().deriveFont(Font.PLAIN, 13f));

        JLabel instructionLabel = new JLabel("Skriv \"" + requiredText + "\" för att bekräfta:");
        instructionLabel.setFont(instructionLabel.getFont().deriveFont(Font.BOLD, 13f));
        instructionLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));

        JTextField confirmField = new JTextField();
        confirmField.setFont(confirmField.getFont().deriveFont(Font.PLAIN, 14f));

        JButton confirmButton = AppButton.create("Bekräfta", AppButton.Variant.DANGER, AppButton.Size.MEDIUM);
        confirmButton.setEnabled(false);
        confirmButton.addActionListener(e -> {
            confirmed = true;
            dispose();
        });

        JButton cancelButton = AppButton.create("Avbryt", AppButton.Variant.OUTLINE, AppButton.Size.MEDIUM);
        cancelButton.addActionListener(e -> dispose());

        // Enable confirm button only when text matches
        confirmField.getDocument().addDocumentListener(new DocumentListener() {
            public void changedUpdate(DocumentEvent e) { check(); }
            public void insertUpdate(DocumentEvent e) { check(); }
            public void removeUpdate(DocumentEvent e) { check(); }
            private void check() {
                confirmButton.setEnabled(confirmField.getText().equals(requiredText));
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonPanel.add(cancelButton);
        buttonPanel.add(confirmButton);

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));
        messageLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        instructionLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmField.setAlignmentX(Component.LEFT_ALIGNMENT);
        confirmField.setMaximumSize(new Dimension(Integer.MAX_VALUE, confirmField.getPreferredSize().height));
        contentPanel.add(messageLabel);
        contentPanel.add(instructionLabel);
        contentPanel.add(confirmField);

        root.add(contentPanel, BorderLayout.CENTER);
        root.add(buttonPanel, BorderLayout.SOUTH);
        setContentPane(root);
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    /**
     * Shows a destructive confirmation dialog and returns whether the user confirmed.
     * @param owner Parent window
     * @param title Dialog title
     * @param message Warning message to display
     * @param requiredText Text the user must type to confirm (e.g., "RADERA")
     * @return true if user confirmed, false if cancelled
     */
    public static boolean show(Frame owner, String title, String message, String requiredText) {
        DestructiveConfirmationDialog dialog = new DestructiveConfirmationDialog(owner, title, message, requiredText);
        dialog.setVisible(true);
        return dialog.isConfirmed();
    }
}
