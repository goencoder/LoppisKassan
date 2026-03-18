package se.goencoder.loppiskassan.ui.dialogs;

import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.ui.AppButton;
import se.goencoder.loppiskassan.ui.AppColors;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.*;

/**
 * Modal dialog for requesting a cashier code with an optional "remember" choice.
 * The code is entered as XXX-XXX using six individual character fields.
 */
public class CashierCodeDialog extends JDialog {

    private static final int GROUP_SIZE = 3;
    private static final int TOTAL_CHARS = GROUP_SIZE * 2;

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
    private final JTextField[] charFields = new JTextField[TOTAL_CHARS];
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

        JLabel messageLabel = new JLabel("<html><div style='width:320px; text-align:center;'>" + message + "</div></html>");
        messageLabel.setFont(messageLabel.getFont().deriveFont(Font.PLAIN, 13f));
        messageLabel.setForeground(AppColors.TEXT_SECONDARY);
        messageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel codeLabel = new JLabel(LocalizationManager.tr("cashier_code.dialog.label"));
        codeLabel.setFont(codeLabel.getFont().deriveFont(Font.BOLD, 12f));
        codeLabel.setForeground(AppColors.TEXT_PRIMARY);
        codeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Build the 6-character input: [ ] [ ] [ ] — [ ] [ ] [ ]
        JPanel codeInputPanel = buildCodeInputPanel();
        codeInputPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        rememberCheckbox = new JCheckBox(LocalizationManager.tr("cashier_code.dialog.remember"));
        rememberCheckbox.setSelected(rememberDefault);
        rememberCheckbox.setOpaque(false);
        rememberCheckbox.setAlignmentX(Component.CENTER_ALIGNMENT);

        JPanel buttonsPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttonsPanel.setOpaque(false);

        JButton cancelButton = new JButton(LocalizationManager.tr("cashier_code.dialog.cancel"));
        AppButton.applyStyle(cancelButton, AppButton.Variant.SECONDARY, AppButton.Size.MEDIUM);
        cancelButton.addActionListener(e -> dispose());

        confirmButton = new JButton(LocalizationManager.tr("cashier_code.dialog.confirm"));
        AppButton.applyStyle(confirmButton, AppButton.Variant.PRIMARY, AppButton.Size.MEDIUM);
        confirmButton.addActionListener(e -> {
            result = new Result(getEnteredCode(), rememberCheckbox.isSelected());
            dispose();
        });

        buttonsPanel.add(cancelButton);
        buttonsPanel.add(confirmButton);

        root.add(messageLabel);
        root.add(Box.createVerticalStrut(16));
        root.add(codeLabel);
        root.add(Box.createVerticalStrut(8));
        root.add(codeInputPanel);
        root.add(Box.createVerticalStrut(12));
        root.add(rememberCheckbox);
        root.add(Box.createVerticalStrut(18));
        root.add(buttonsPanel);

        setContentPane(root);
        getRootPane().setDefaultButton(confirmButton);
        updateConfirmState();

        // Focus first field on open
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                charFields[0].requestFocusInWindow();
            }
        });
    }

    private JPanel buildCodeInputPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 0));
        panel.setOpaque(false);

        Font charFont = new Font(Font.MONOSPACED, Font.BOLD, 22);
        Dimension fieldSize = new Dimension(38, 40);

        for (int i = 0; i < TOTAL_CHARS; i++) {
            if (i == GROUP_SIZE) {
                JLabel dash = new JLabel("—");
                dash.setFont(charFont);
                dash.setForeground(AppColors.TEXT_MUTED);
                dash.setBorder(new EmptyBorder(0, 4, 0, 4));
                panel.add(dash);
            }

            JTextField field = new JTextField(1);
            field.setFont(charFont);
            field.setHorizontalAlignment(JTextField.CENTER);
            field.setPreferredSize(fieldSize);
            field.setMinimumSize(fieldSize);
            field.setMaximumSize(fieldSize);
            field.setBackground(AppColors.FIELD_BG);

            // Limit to single uppercase alphanumeric character
            ((AbstractDocument) field.getDocument()).setDocumentFilter(new SingleCharFilter());

            final int index = i;

            // Auto-advance on input
            field.getDocument().addDocumentListener(new DocumentListener() {
                public void insertUpdate(DocumentEvent e) {
                    updateConfirmState();
                    if (field.getText().length() == 1 && index < TOTAL_CHARS - 1) {
                        SwingUtilities.invokeLater(() -> charFields[index + 1].requestFocusInWindow());
                    }
                }
                public void removeUpdate(DocumentEvent e) { updateConfirmState(); }
                public void changedUpdate(DocumentEvent e) { updateConfirmState(); }
            });

            // Backspace on empty field goes to previous
            field.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE && field.getText().isEmpty() && index > 0) {
                        charFields[index - 1].requestFocusInWindow();
                        charFields[index - 1].setText("");
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_LEFT && index > 0) {
                        charFields[index - 1].requestFocusInWindow();
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_RIGHT && index < TOTAL_CHARS - 1) {
                        charFields[index + 1].requestFocusInWindow();
                        e.consume();
                    }
                }
            });

            // Handle paste: distribute pasted text across fields
            field.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
                    if ((e.getModifiersEx() & mask) != 0 && e.getKeyCode() == KeyEvent.VK_V) {
                        handlePaste();
                        e.consume();
                    }
                }
            });

            // Select all text on focus for easy overwrite
            field.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    SwingUtilities.invokeLater(field::selectAll);
                }
            });

            charFields[i] = field;
            panel.add(field);
        }

        return panel;
    }

    private void handlePaste() {
        try {
            String clip = (String) Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
            if (clip == null) return;
            // Clear all fields first to avoid stale characters from a previous longer code
            for (int i = 0; i < TOTAL_CHARS; i++) {
                charFields[i].setText("");
            }
            // Strip dashes and spaces, uppercase
            String clean = clip.replaceAll("[\\s-]", "").toUpperCase();
            for (int i = 0; i < Math.min(clean.length(), TOTAL_CHARS); i++) {
                char c = clean.charAt(i);
                if (Character.isLetterOrDigit(c)) {
                    charFields[i].setText(String.valueOf(c));
                }
            }
            // Focus last filled field or the next empty one
            int focusIdx = Math.min(clean.length(), TOTAL_CHARS) - 1;
            if (focusIdx >= 0 && focusIdx < TOTAL_CHARS) {
                charFields[focusIdx].requestFocusInWindow();
            }
        } catch (Exception ignored) {
            // clipboard not available
        }
    }

    private String getEnteredCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < TOTAL_CHARS; i++) {
            if (i == GROUP_SIZE) sb.append('-');
            sb.append(charFields[i].getText().trim());
        }
        return sb.toString();
    }

    private void updateConfirmState() {
        if (confirmButton == null) return;
        boolean allFilled = true;
        for (JTextField f : charFields) {
            if (f == null || f.getText().trim().isEmpty()) {
                allFilled = false;
                break;
            }
        }
        confirmButton.setEnabled(allFilled);
    }

    /**
     * Document filter that allows only a single uppercase alphanumeric character.
     */
    private static class SingleCharFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr)
                throws BadLocationException {
            replace(fb, 0, fb.getDocument().getLength(), string, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs)
                throws BadLocationException {
            if (text == null || text.isEmpty()) {
                super.replace(fb, offset, length, text, attrs);
                return;
            }
            // Take only first alphanumeric char, uppercase it, replace entire content
            for (char c : text.toCharArray()) {
                if (Character.isLetterOrDigit(c)) {
                    fb.replace(0, fb.getDocument().getLength(), String.valueOf(Character.toUpperCase(c)), attrs);
                    return;
                }
            }
        }
    }
}
