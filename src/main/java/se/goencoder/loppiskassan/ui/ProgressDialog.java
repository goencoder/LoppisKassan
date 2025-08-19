package se.goencoder.loppiskassan.ui;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

public class ProgressDialog {
    private final JDialog dialog;
    private final JProgressBar progressBar;

    private ProgressDialog(Frame parent, String title, String message) {
        dialog = new JDialog(parent, title, true /* modal */);
        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.setLayout(new BorderLayout(10, 10));
        dialog.setResizable(false);

        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel label = new JLabel(message, SwingConstants.CENTER);
        progressBar = new JProgressBar();
        progressBar.setIndeterminate(true);

        panel.add(label, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);

        dialog.add(panel, BorderLayout.CENTER);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
    }

    /**
     * Runs a background task with a spinner dialog in the foreground.
     *
     * @param parent     A component for anchoring the modal dialog
     * @param title      The dialog title
     * @param message    The message shown above the spinner
     * @param task       The background operation (off the EDT)
     * @param onSuccess  Callback with the result when task completes (on the EDT)
     * @param onFailure  Callback if an exception occurred (on the EDT)
     */
    public static <T> void runTask(
            Component parent,
            String title,
            String message,
            Callable<T> task,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure
    ) {
        // Find the top-level parent frame
        Frame frame = (parent instanceof Frame)
                ? (Frame) parent
                : (Frame) SwingUtilities.getWindowAncestor(parent);

        ProgressDialog pd = new ProgressDialog(frame, title, message);

        SwingWorker<T, Void> worker = new SwingWorker<>() {
            private Throwable thrown;

            @Override
            protected T doInBackground() {
                try {
                    return task.call();
                } catch (Exception ex) {
                    thrown = ex;
                    return null; // No result if an exception
                }
            }

            @Override
            protected void done() {
                // This runs on the EDT once doInBackground() completes
                pd.closeDialog();
                if (thrown != null) {
                    if (onFailure != null) onFailure.accept(thrown);
                } else {
                    try {
                        T result = get(); // retrieve doInBackground() result
                        if (onSuccess != null) onSuccess.accept(result);
                    } catch (Exception ex) {
                        if (onFailure != null) onFailure.accept(ex);
                    }
                }
            }
        };

        // 1. Schedule the background work to begin
        worker.execute();

        // 2. Now show the modal dialog. This starts the AWT modal loop,
        //    but the SwingWorker is already running on a separate thread.
        //    The spinner remains animated since the dialog's internal event loop continues painting.
        pd.dialog.setVisible(true);
    }

    private void closeDialog() {
        dialog.dispose();
    }
}
