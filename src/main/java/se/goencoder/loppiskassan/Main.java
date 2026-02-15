package se.goencoder.loppiskassan;


import se.goencoder.loppiskassan.config.AppMode;
import se.goencoder.loppiskassan.config.AppModeManager;
import se.goencoder.loppiskassan.records.FileHelper;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.ui.AppShellFrame;
import se.goencoder.loppiskassan.ui.Theme;
import se.goencoder.loppiskassan.ui.dialogs.ModeSelectionDialog;
import se.goencoder.loppiskassan.util.AppPaths;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.io.IOException;
import java.util.logging.*;

/**
 * Huvudklassen för applikationen Loppiskassan i Swing.
 */
public class Main {
    private static boolean loggerCreated = false;
    /**
     * Initierar och visar huvudfönstret för Swing-applikationen.
     */
    private static void createAndShowGUI() {
        Theme.install(); // Install look & feel before creating components
        setDockIcon(); // Set macOS dock icon before any windows appear

        // Show splash screen to select operating mode
        ModeSelectionDialog splash = new ModeSelectionDialog();
        AppMode mode = splash.showDialog();
        if (mode == null) {
            System.exit(0); // User closed splash without choosing
            return;
        }
        AppModeManager.setMode(mode);

        AppShellFrame frame = new AppShellFrame();
        frame.setVisible(true);
    }


    /**
     * Sets the macOS dock icon and stores the image for JFrame icon use.
     * Must be called before any window is shown so the dock icon is correct from the start.
     */
    static java.awt.Image appIconImage;

    private static void setDockIcon() {
        try {
            var iconUrl = Main.class.getClassLoader().getResource("images/iloppis-icon.png");
            if (iconUrl == null) {
                System.err.println("Icon resource not found: images/iloppis-icon.png");
                return;
            }
            appIconImage = ImageIO.read(iconUrl);

            // Set macOS dock icon
            if (java.awt.Taskbar.isTaskbarSupported()) {
                var taskbar = java.awt.Taskbar.getTaskbar();
                if (taskbar.isSupported(java.awt.Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.setIconImage(appIconImage);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to set dock icon: " + e.getMessage());
        }
    }

    /**
     * Returns the loaded app icon, or null if not yet loaded.
     */
    public static java.awt.Image getAppIconImage() {
        return appIconImage;
    }

    public static void createLogger() throws IOException {
        if (loggerCreated) {
            return;
        }
        loggerCreated = true;
        FileHandler mFileHandler;
        final Logger parentLogger = Logger.getAnonymousLogger().getParent();
        parentLogger.setLevel(Level.INFO); // Set the root logger to capture all levels
        final Handler[] handlers = parentLogger.getHandlers();
        for (final Handler handler : handlers) {
            if (handler instanceof ConsoleHandler) {
                parentLogger.removeHandler(handler);
            }
        }
        final String pathPattern = FileHelper.getLogFilePath().toString();
        mFileHandler = new FileHandler(pathPattern, 1400000, 5, true);
        mFileHandler.setFormatter(new SimpleFormatter());
        parentLogger.addHandler(mFileHandler);
        final ConsoleHandler stdConsoleHandler = new ConsoleHandler();
        mFileHandler.setLevel(Level.FINE);
        stdConsoleHandler.setLevel(Level.FINE);
        parentLogger.addHandler(stdConsoleHandler);
    }
    /**
     * Huvudmetoden för applikationen.
     * Skapar de nödvändiga katalogerna, skapar loggern och startar Swing-applikationen.
     */
    public static void main(String[] args) {
        // Omge med try-catch för att hantera IO-undantag
        try {
            AppPaths.migrateLegacyPaths();
            FileHelper.createDirectories();
            createLogger();

            // Initialize localization system with proper language configuration
            LocalizationManager.initialize();

            // Schemalägg jobbet för händelseavkodningstråden (EDT)
            SwingUtilities.invokeLater(Main::createAndShowGUI);
        } catch (IOException e) {
            // We have no logs yet, so we dump the error to the console
            e.printStackTrace();
            Popup.FATAL.showAndWait(
                    LocalizationManager.tr("error.create_dirs.title"),
                    LocalizationManager.tr("error.create_dirs.message"));
        }
    }
}
