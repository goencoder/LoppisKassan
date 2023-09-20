package se.teddy.loppiskassan;

import javafx.scene.control.Alert;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Representerar de olika popup-fönstertyperna som kan visas i applikationen Loppiskassan.
 * <p>
 * Denna enum tillhandahåller en bekvämlighetsmetod för att visa fönster baserat på typen av popup.
 * </p>
 *
 * @author gengdahl
 * @since 2016-09-24
 */
public enum Popup {
    /**
     * Popup för att visa felmeddelanden.
     */
    ERROR(javafx.scene.control.Alert.AlertType.ERROR),
    /**
     * Popup för att visa informativa meddelanden.
     */
    INFORMATION(javafx.scene.control.Alert.AlertType.INFORMATION),
    /**
     * Popup för att visa varningsmeddelanden.
     */
    WARNING(Alert.AlertType.WARNING);
    private javafx.scene.control.Alert.AlertType type;
    /**
     * Skapar en ny popup med den givna typen.
     *
     * @param type Typ av popup.
     */
    private Popup(javafx.scene.control.Alert.AlertType type){
        this.type = type;
    }
    /**
     * Visar popup-fönstret och väntar tills användaren stänger det.
     * <p>
     * Om det givna objektet är en exception, kommer en stack trace att visas.
     * </p>
     *
     * @param title Titel på popup-fönstret.
     * @param information Innehållet i popup-fönstret. Kan vara en textsträng eller en exception.
     */
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
