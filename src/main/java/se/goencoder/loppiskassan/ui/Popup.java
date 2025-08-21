package se.goencoder.loppiskassan.ui;

import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;
import java.awt.Dimension;

import org.json.JSONObject;
import se.goencoder.iloppis.invoker.ApiException;
import se.goencoder.loppiskassan.localization.LocalizationManager;


/**
 * Enum representing different types of popup windows that can be displayed in the Loppiskassan Swing application.
 */
public enum Popup {
    ERROR(JOptionPane.ERROR_MESSAGE), // Represents an error message dialog
    INFORMATION(JOptionPane.INFORMATION_MESSAGE), // Represents an informational message dialog
    WARNING(JOptionPane.WARNING_MESSAGE), // Represents a warning message dialog

    FATAL(JOptionPane.ERROR_MESSAGE),
    // Add a new constant for confirmation dialogs
    CONFIRM(JOptionPane.QUESTION_MESSAGE);
    private static final Logger logger = Logger.getLogger(Popup.class.getName());


    private final int messageType;

    /**
     * Constructs a new popup with the given type for use in Swing.
     *
     * @param messageType The type of popup, adjusted for Swing usage.
     */
    Popup(int messageType) {
        this.messageType = messageType;
    }

    /**
     * Displays the popup window and waits until the user closes it.
     * If the provided object is an exception, a stack trace will be displayed.
     *
     * @param title       The title of the popup window.
     * @param information The content of the popup window. Can be a string or an exception.
     */
    public void showAndWait(String title, Object information) {
        String info = null;
        if (information instanceof ApiException) {
            String body = ((ApiException) information).getResponseBody();
            if (body != null && !body.isEmpty()) {
                try {
                    JSONObject jsonObj = new JSONObject(body);
                    String language = LocalizationManager.getLanguage();
                    String localizedMessage = null;
                    if (jsonObj.has("details")) {
                        for (Object detailObj : jsonObj.getJSONArray("details")) {
                            if (detailObj instanceof JSONObject) {
                                JSONObject detail = (JSONObject) detailObj;
                                if ("type.googleapis.com/google.rpc.LocalizedMessage".equals(detail.optString("@type"))) {
                                    String localeStr = detail.optString("locale");
                                    Locale detailLocale = Locale.forLanguageTag(localeStr.replace('_', '-'));
                                    if (language.equalsIgnoreCase(detailLocale.getLanguage())) {
                                        localizedMessage = detail.optString("message", null);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (localizedMessage != null) {
                        info = localizedMessage;
                    } else {
                        info = ((ApiException)information).getMessage();
                    }
                } catch (org.json.JSONException ex) {
                    logger.warning("Failed to parse JSON from ApiException: " + ex.getMessage());
                    info = ex.getMessage();
                }
            } else {
                info = LocalizationManager.tr("error.api_exception.default");
            }
        } else if (information instanceof Exception) {
            // Convert the stack trace of an exception into a string
            StringWriter sw = new StringWriter();
            ((Exception) information).printStackTrace(new PrintWriter(sw));
            info = "See log file for more details:\n" + sw;
        } else if (information != null) {
            // Convert the information object to a string if it's not an exception
            info = information.toString();
        }


        // Use a scrollable, EDT-safe dialog (no hard truncation)
        final String text = Objects.requireNonNullElse(info, "");
        Runnable show = () -> {
            JTextArea area = new JTextArea(text);
            area.setEditable(false);
            area.setLineWrap(true);
            area.setWrapStyleWord(true);
            area.setOpaque(false);
            JScrollPane sp = new JScrollPane(area);
            sp.setBorder(null);
            // Sensible size for long messages; still resizable by the user
            sp.setPreferredSize(new Dimension(600, Math.min(400, Math.max(160, area.getLineCount() * 18))));
            JOptionPane.showMessageDialog(null, sp, title, messageType);
        };
        if (SwingUtilities.isEventDispatchThread()) {
            show.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(show);
            } catch (Exception ignored) {
                // Fallback if invokeAndWait fails
                JOptionPane.showMessageDialog(null, text, title, messageType);
            }
        }
        if (this == INFORMATION) {
            logger.info(title + ": " + info);
        } else if (this == ERROR) {
            logger.severe(title + ": " + info);
        } else if (this == WARNING) {
            logger.warning(title + ": " + info);
        } else if (this == FATAL) {
            logger.severe(title + ": " + info);
            System.exit(-1);
        }
    }
    /**
     * Shows a confirmation dialog with "Confirm" and "Reject" options.
     *
     * @param title       The title of the dialog.
     * @param message     The message to display in the dialog.
     * @return            true if the user clicks "Confirm", false otherwise.
     */
    public boolean showConfirmDialog(String title, String message) {
        // Customize button texts as needed
        Object[] options = {LocalizationManager.tr("popup.confirm"), LocalizationManager.tr("popup.cancel")};
        int result = JOptionPane.showOptionDialog(null, message, title,
                JOptionPane.DEFAULT_OPTION, messageType,
                null, options, options[0]);
        return result == 0; // Returns true if "Confirm" (first option) is clicked
    }
}
