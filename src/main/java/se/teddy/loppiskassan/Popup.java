package se.teddy.loppiskassan;

import javafx.scene.control.Alert;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Created by gengdahl on 2016-09-24.
 */
public enum Popup {
    ERROR(javafx.scene.control.Alert.AlertType.ERROR),
    INFORMATION(javafx.scene.control.Alert.AlertType.INFORMATION),
    WARNING(Alert.AlertType.WARNING);
    private javafx.scene.control.Alert.AlertType type;
    private Popup(javafx.scene.control.Alert.AlertType type){
        this.type = type;
    }
    public void showAndWait(String title, Object information){
        String info = null;
        if (information != null){
            info = information.toString();
            if (information instanceof Exception){
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                ((Exception)information).printStackTrace(pw);

                info = "Se logfil för mer detaljer:\n" + sw.toString();
                info = info.substring(0, Math.min(info.length(), 400));

            }
        }

        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(type);
        alert.setTitle(title);
        if (info != null){
            alert.setContentText(info);

        }
        alert.showAndWait();
    }
}
