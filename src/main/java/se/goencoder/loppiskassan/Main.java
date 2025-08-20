package se.goencoder.loppiskassan;


import se.goencoder.loppiskassan.records.FileHelper;
import se.goencoder.loppiskassan.localization.LocalizationManager;
import se.goencoder.loppiskassan.ui.Popup;
import se.goencoder.loppiskassan.ui.UserInterface;
import se.goencoder.loppiskassan.ui.Theme;

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
        UserInterface frame = new UserInterface();
        frame.setTitle(LocalizationManager.tr("frame.title"));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Ställ in ramens storlek och gör den synlig
        frame.setSize(640, 600);
        frame.setLocationRelativeTo(null); // Centrerar fönstret
        frame.setVisible(true);
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
