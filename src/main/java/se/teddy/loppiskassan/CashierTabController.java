package se.teddy.loppiskassan;

import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import se.teddy.loppiskassan.records.FileHelper;
import se.teddy.loppiskassan.records.FormatHelper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Denna kontroller hanterar funktionaliteten för "Kassörskort"-delen av användargränssnittet.
 * Den låter användaren lägga till sålda varor, beräkna växel och genomföra transaktioner.
 *
 * @author gengdahl
 * @since 2016-09-12
 */
public class CashierTabController {
    @FXML
    private TextField prices;
    @FXML
    private TableView cashierTable;
    @FXML
    private TextField seller;
    @FXML
    private TextField payedCash;
    @FXML
    private TextField changeCash;
    @FXML
    private Label sumLabel;
    @FXML
    private Label noItemsLabel;
    @FXML
    private Button checkoutCash;
    @FXML
    private Button checkoutSwish;
    @FXML
    private Button cancelCheckout;

    private static CashierTabController instance;

    /**
     * Konstruktor för CashierTabController. Garanterar att endast en instans skapas.
     */
    public CashierTabController(){
        if (instance != null){
            throw new IllegalArgumentException("Controller already instantiated, only one instance is allowed!");
        }
        instance = this;
    }
    public static CashierTabController getInstance(){
        return instance;
    }
    public void initUI() {
        focusOnPrices();
        updateUIComponents();
    }


    private ObservableList<SoldItem> items;


    /**
     * Sätter fokus på "pris"-fältet.
     */
    public void focusOnPrices() {
        prices.requestFocus();
    }
    /**
     * Lägger till en artikel baserat på inmatad information.
     */
    public void addItem() {
        FileHelper.assertRecordFileRights();

        bindDeleteOnTable(cashierTable);
        items = cashierTable.getItems();
        int sellerId = Integer.parseInt(seller.getText());
        String[] stringPrices = prices.getText().split(" ");
        for (String price : stringPrices) {
            items.add(0,new SoldItem(sellerId, Float.parseFloat(price), null));
        }
        prices.clear();
        seller.clear();
        seller.requestFocus();
        int intSum = (int) getSum();
        int synthPayed = ((intSum + 99) / 100) * 100;
        payedCash.setText(String.valueOf(synthPayed));
        updateUIComponents();
    }
    public void calculateChange() {
        updateUIComponents();

    }
    public void checkout(PaymentMethod paymentMethod) {
        LocalDateTime now = LocalDateTime.now();
        String purchaseId = UUID.randomUUID().toString();
        for (SoldItem item : items) {
            item.setSoldTime(now);
            item.setPaymentMethod(paymentMethod);
            item.setPurchaseId(purchaseId);
        }
        try {
            FileHelper.saveToFile(FormatHelper.toCVS(items));
            items.clear();
            payedCash.clear();
            updateUIComponents();
            seller.requestFocus();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }
    public void checkoutSwish() {
        checkout(PaymentMethod.Swish);
    }
    public void checkoutCash() {
        checkout(PaymentMethod.Kontant);
    }
    public void cancelCheckout() {
        items.clear();
        payedCash.clear();
        updateUIComponents();
        seller.requestFocus();

    }

    private void bindDeleteOnTable(TableView table) {
        table.setOnKeyPressed(new EventHandler<KeyEvent>() {
            @Override
            public void handle(final KeyEvent keyEvent) {
                final SoldItem selectedItem = (SoldItem) table.getSelectionModel().getSelectedItem();

                if (selectedItem != null) {
                    if (keyEvent.getCode().equals(KeyCode.DELETE)) {
                        items.remove(selectedItem);
                        updateUIComponents();
                        seller.requestFocus();
                    }
                }
            }
        });
    }
    /**
     * Uppdaterar användargränssnittskomponenter baserat på aktuell data.
     */
    private void updateUIComponents() {
        //Buttons
        if (cashierTable.getItems().isEmpty()){
            checkoutCash.setDisable(true);
            checkoutSwish.setDisable(true);
            cancelCheckout.setDisable(true);
            cashierTable.setFocusTraversable(false);
        }else{
            checkoutCash.setDisable(false);
            checkoutSwish.setDisable(false);
            cancelCheckout.setDisable(false);
            cashierTable.setFocusTraversable(true);
        }
        //Labels
        if (items == null){
            items = cashierTable.getItems();
        }
        Font font = sumLabel.getFont();
        sumLabel.setFont(Font.font(null, FontWeight.BOLD, font.getSize()));
        float sum = getSum();
        int numOfItems = items.size();
        changeCash.setText("");
        if (!payedCash.getText().isEmpty()) {
            float cash = Float.parseFloat(payedCash.getText());
            if (cash > sum) {
                changeCash.setText("" + (cash - sum));
            }
        }
        sumLabel.setText(String.valueOf(sum) + " SEK");
        noItemsLabel.setText(numOfItems + " varor");

    }
    /**
     * Beräknar den totala summan av de sålda varorna.
     *
     * @return den totala summan av de sålda varorna.
     */
    private float getSum() {
        float sum = 0;
        if (items != null){
            for (SoldItem item : items) {
                sum += item.getPrice();
            }
        }
        return sum;
    }



}
