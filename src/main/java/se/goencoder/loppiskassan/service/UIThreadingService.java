package se.goencoder.loppiskassan.service;

import javax.swing.SwingUtilities;

/**
 * Service for managing UI thread operations.
 * Isolates Swing threading concerns from controllers.
 * <p>
 * Controllers should not import javax.swing.SwingUtilities directly.
 * Instead, use this service to schedule UI updates on the Event Dispatch Thread.
 * </p>
 */
public final class UIThreadingService {
    
    private UIThreadingService() {
        // Utility class
    }
    
    /**
     * Execute a task on the Event Dispatch Thread.
     * If already on EDT, runs immediately. Otherwise, schedules via invokeLater.
     * 
     * @param task the task to run on EDT
     */
    public static void runOnUIThread(Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
    
    /**
     * Schedule a task to run later on the Event Dispatch Thread.
     * 
     * @param task the task to schedule on EDT
     */
    public static void invokeLater(Runnable task) {
        SwingUtilities.invokeLater(task);
    }
    
    /**
     * Check if currently running on the Event Dispatch Thread.
     * 
     * @return true if on EDT, false otherwise
     */
    public static boolean isOnUIThread() {
        return SwingUtilities.isEventDispatchThread();
    }
}
