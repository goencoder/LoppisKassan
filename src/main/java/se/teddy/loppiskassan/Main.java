package se.teddy.loppiskassan;

import javafx.application.Application;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.TabPane;
import javafx.stage.Stage;
import se.teddy.loppiskassan.records.FileHelper;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class Main extends Application {

    private HistoryTabController historyTabController;
    @FXML
    HistoryTabController historyTabPageController;

    @Override
    public void start(Stage primaryStage) throws Exception{
        FXMLLoader mainLoader = new FXMLLoader(
                getClass().getResource(
                        "userInterface.fxml"
                )
        );

        TabPane pane = (TabPane) mainLoader.load();

        Controller mainController =
                mainLoader.<Controller>getController();
        Controller.setStage(primaryStage);
        primaryStage.setTitle("Loppiskassan v1.2 (" + FileHelper.getRecordFilePath().toString() +")");
        primaryStage.setScene(new Scene(pane, 640, 600));
        mainController.initUI();
        primaryStage.show();
    }
    private static boolean loggerCreated = false;

    public static void createLogger() throws IOException {
        if (loggerCreated) {
            return;
        }
        loggerCreated = true;
        FileHandler mFileHandler = null;
        final Logger parentLogger = Logger.getAnonymousLogger().getParent();
        final Handler[] handlers = parentLogger.getHandlers();
        for (int i = 0; i < handlers.length; i++) {
            final Handler handler = handlers[i];
            if (handler instanceof ConsoleHandler) {
                parentLogger.removeHandler(handler);
            }
        }
        try {
            final String pathPattern = FileHelper.getLogFilePath().toString();
            mFileHandler = new FileHandler(pathPattern, 1400000, 5, true);
            mFileHandler.setFormatter(new SimpleFormatter());
            parentLogger.addHandler(mFileHandler);
            final ConsoleHandler stdConsoleHandler = new ConsoleHandler();
            mFileHandler.setLevel(Level.FINE);
            stdConsoleHandler.setLevel(Level.FINE);
            parentLogger.addHandler(stdConsoleHandler);
        }
        catch (final Exception e) {
            e.printStackTrace();
            throw e;
        }
    }


    public static void main(String[] args) throws IOException {
        FileHelper.createDirectories();
        createLogger();
        launch(args);
    }
}
