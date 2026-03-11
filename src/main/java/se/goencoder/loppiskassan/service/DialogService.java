package se.goencoder.loppiskassan.service;

import java.awt.Component;
import java.awt.Frame;

/**
 * Service for managing dialog parent components.
 * Isolates AWT/Swing frame resolution from controllers.
 * <p>
 * Controllers should not import java.awt.Frame or query Frame.getFrames() directly.
 * Instead, use this service to find appropriate parent components for dialogs.
 * </p>
 */
public final class DialogService {
    
    private DialogService() {
        // Utility class
    }
    
    /**
     * Find the main application frame to use as a parent for dialogs.
     * Searches for the first displayable and visible frame.
     * 
     * @return the main frame, or null if no suitable frame found
     */
    public static Component findMainFrame() {
        for (Frame frame : Frame.getFrames()) {
            if (frame.isDisplayable() && frame.isVisible()) {
                return frame;
            }
        }
        return null;
    }
    
    /**
     * Get a component suitable for use as a dialog parent.
     * Uses the main frame if available, otherwise null.
     * 
     * @return a component for dialog parenting, or null
     */
    public static Component getDialogParent() {
        return findMainFrame();
    }
}
