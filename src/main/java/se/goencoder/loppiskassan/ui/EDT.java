package se.goencoder.loppiskassan.ui;

import javax.swing.SwingUtilities;

/**
 * Utility to ensure code runs on the Swing Event Dispatch Thread.
 */
public final class EDT {
    private EDT() {}

    /**
     * Run the given runnable on the Event Dispatch Thread.
     *
     * @param r runnable to execute
     */
    public static void run(Runnable r) {
        if (SwingUtilities.isEventDispatchThread()) {
            r.run();
        } else {
            SwingUtilities.invokeLater(r);
        }
    }
}
