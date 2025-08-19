package se.goencoder.loppiskassan.ui;

import java.awt.*;

public interface UiComponent {
    /**
     * Returns the root component of this panel for use with dialogs and spinners.
     *
     * @return The root component of this panel.
     */
    Component getComponent();
}
