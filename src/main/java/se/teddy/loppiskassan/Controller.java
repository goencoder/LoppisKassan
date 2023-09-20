package se.teddy.loppiskassan;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.stage.Stage;
import se.teddy.loppiskassan.records.FileHelper;

import java.io.IOException;
import java.util.*;

public class Controller {
    private static Stage stage;
    @FXML
    private TabPane tabPane;


    static void setStage(Stage stage) {
        Controller.stage = stage;
    }
    public void initUI() {


        tabPane.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<Tab>() {

            @Override
            public void changed(ObservableValue<? extends Tab> observable, Tab oldTab, Tab newTab) {
                String tabText = newTab.getText();
                switch(tabText){
                    case "Historik": HistoryTabController.getInstance().initUI(); break;
                    case "Kassa": CashierTabController.getInstance().initUI(); break;
                }
            }
        });
        CashierTabController.getInstance().initUI();
    }


}
