package se.goencoder.loppiskassan.ui;

import javax.swing.JOptionPane;
import javax.swing.JTextArea;
import javax.swing.JScrollPane;
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

    public static void info(String key, Object... args) {
        INFORMATION.showAndWait(LocalizationManager.tr(key + ".title"), LocalizationManager.tr(key + ".message", args));
    }

    public static void warn(String key, Object... args) {
        WARNING.showAndWait(LocalizationManager.tr(key + ".title"), LocalizationManager.tr(key + ".message", args));
    }

    public static void error(String key, Object... args) {
        ERROR.showAndWait(LocalizationManager.tr(key + ".title"), LocalizationManager.tr(key + ".message", args));
    }

    /**
     * Display a popup with optional expandable details.
     *
     * @param title   dialog title
     * @param content main message or exception
     */
    public void showAndWait(String title, Object content) {
        String message = null;
        String details = null;
        if (content instanceof ApiException api) {
            String body = api.getResponseBody();
            if (body != null && !body.isEmpty()) {
                try {
                    JSONObject jsonObj = new JSONObject(body);
                    String language = LocalizationManager.getLanguage();
                    if (jsonObj.has("details")) {
                        for (Object detailObj : jsonObj.getJSONArray("details")) {
                            if (detailObj instanceof JSONObject detail) {
                                if ("type.googleapis.com/google.rpc.LocalizedMessage".equals(detail.optString("@type"))) {
                                    String localeStr = detail.optString("locale");
                                    Locale detailLocale = Locale.forLanguageTag(localeStr.replace('_', '-'));
                                    if (language.equalsIgnoreCase(detailLocale.getLanguage())) {
                                        message = detail.optString("message", null);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    if (message == null) {
                        message = api.getMessage();
                    }
                } catch (org.json.JSONException ex) {
                    logger.warning("Failed to parse JSON from ApiException: " + ex.getMessage());
                    message = ex.getMessage();
                }
            } else {
                message = LocalizationManager.tr("error.api_exception.default");
            }
        } else if (content instanceof Exception ex) {
            message = ex.getMessage();
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            details = sw.toString();
        } else if (content != null) {
            message = content.toString();
        }

        final String msg = Objects.requireNonNullElse(message, "");
        final String det = details;
        EDT.run(() -> {
            if (det != null && !det.isBlank()) {
                Object[] options = {LocalizationManager.tr("popup.ok"), LocalizationManager.tr("popup.details")};
                int res = JOptionPane.showOptionDialog(null, msg, title, JOptionPane.DEFAULT_OPTION, messageType,
                        null, options, options[0]);
                if (res == 1) {
                    JTextArea area = new JTextArea(det);
                    area.setEditable(false);
                    area.setLineWrap(true);
                    area.setWrapStyleWord(true);
                    JScrollPane sp = new JScrollPane(area);
                    sp.setPreferredSize(new Dimension(420, 240));
                    JOptionPane.showMessageDialog(null, sp, title, messageType);
                }
            } else {
                JOptionPane.showMessageDialog(null, msg, title, messageType);
            }
        });

        if (this == INFORMATION) {
            logger.info(title + ": " + msg);
        } else if (this == ERROR) {
            logger.severe(title + ": " + msg);
        } else if (this == WARNING) {
            logger.warning(title + ": " + msg);
        } else if (this == FATAL) {
            logger.severe(title + ": " + msg);
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
