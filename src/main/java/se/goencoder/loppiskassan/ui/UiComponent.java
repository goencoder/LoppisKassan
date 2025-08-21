package se.goencoder.loppiskassan.ui;

import java.awt.*;

/**
 * Common contract for Swing-based panels that expose a root {@link java.awt.Component}.
 */
public interface UiComponent {

    /**
     * Return the root Swing component for this view.
     * <p>
     * This is typically the panel returned to container layouts (tabs, dialogs, etc.).
     * Implementations should return the same instance consistently.
     *
     * @return the root {@link java.awt.Component} of this panel
     */
    Component getComponent();
}
