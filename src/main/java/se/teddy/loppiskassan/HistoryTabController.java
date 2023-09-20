package se.teddy.loppiskassan;

import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import se.teddy.loppiskassan.records.FileHelper;
import se.teddy.loppiskassan.records.FormatHelper;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Created by gengdahl on 2016-09-13.
 */
public class HistoryTabController {
  private static Stage stage;
  private static HistoryTabController instance;
  @FXML
  private ChoiceBox<String> historySellerChoice;
  @FXML
  private ChoiceBox<String> historyPaymentChoice;
  @FXML
  private CheckBox historyHidePayedOutItems;
  @FXML
  private Label historySumLabel;
  @FXML
  private Label historyNoPurchaseLabel;
  @FXML
  private Label historyNoSwishPurchaseLabel;
  @FXML
  private Label historyNoItemsLabel;
  @FXML
  private Label historyProvisioningLabel;
  @FXML
  private Label historyToBePayedLabel;
  @FXML
  private TableView historyTable;
  @FXML
  private Button payoutButton;
  @FXML
  private Button toClipboardButton;
  private List<SoldItem> allHistoryItems;
  private Set<String> uuidSet = new HashSet<String>();
  private ObservableList<SoldItem> historyItems;
  private int historyFilteredseller = -1;
  private List<PaymentMethod> historyFilteredPaymentOption = new ArrayList(0);

  public HistoryTabController() {
    if (instance != null) {
      throw new IllegalArgumentException("Controller already instanciated, only one instance is allowed!");
    }
    instance = this;
  }

  public static HistoryTabController getInstance() {
    return instance;
  }

  static void setStage(Stage stage) {
    HistoryTabController.stage = stage;
  }


  public void eraseAllData() {

    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle("Rensa kassan?");
    String s = "Alla köp kommer tas bort, är du säker?";
    alert.setContentText(s);

    Optional<ButtonType> result = alert.showAndWait();

    if ((result.isPresent()) && (result.get() == ButtonType.OK)) {
      try {
        FileHelper.deleteFile();
        allHistoryItems.clear();
        populateHistoryTable();
        updateHistoryLabels();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
  }

  public void importData() {
    initUI();
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Öppna annan kassa-fil");
    File file = fileChooser.showOpenDialog(stage);
    if (file != null) {
      try {
        List<SoldItem> importedItems = FormatHelper.toItems(FileHelper.readFromFile(file.toPath()), true);
        int numberOfImportedItems = 0;
        for (SoldItem item : importedItems) {
          if (!allHistoryItems.contains(item)) {
            allHistoryItems.add(item);
            numberOfImportedItems++;

          }
        }
        FileHelper.createBackupFile();
        FileHelper.saveToFile(FormatHelper.toCVS(allHistoryItems));
        populateHistoryTable();
        updateHistoryLabels();
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Import klar!");
        String info = "Importerade " + numberOfImportedItems + " av " + importedItems.size() + " poster";
        alert.setContentText(info);
        alert.showAndWait();

      } catch (Exception e1) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        String error = "Importering misslyckades";
        alert.setTitle(error);
        alert.setContentText(error);
        alert.showAndWait();
        e1.printStackTrace();
      }
    }

  }

  public void doPayout() {
    LocalDateTime now = LocalDateTime.now();
    for (SoldItem tableItem : historyItems) {
      SoldItem item = allHistoryItems.get(allHistoryItems.indexOf(tableItem));
      if (!item.isCollectedBySeller()) {
        item.setCollectedBySellerTime(now);
      }
    }
    try {
      FileHelper.createBackupFile();
      FileHelper.saveToFile(FormatHelper.toCVS(allHistoryItems));
      populateHistoryTable();
      updateHistoryLabels();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public void toClipboard() {
    final Clipboard clipboard = Clipboard.getSystemClipboard();
    final ClipboardContent content = new ClipboardContent();
    final String NL = System.lineSeparator();
    StringBuilder sb = new StringBuilder();
    int numberOfItems = historyItems.size();
    int sum = 0;
    int provision = 0;
    int index = 1;
    StringBuilder itemsDetailed = new StringBuilder();
    for (SoldItem item : historyItems) {
      sum += item.getPrice();
      itemsDetailed.append(index++).append(".\t").append(item.getPrice()).append(" SEK ")
        .append(item.isCollectedBySeller() ? "Utbetalt" : "Ej utbetalt").append(System.lineSeparator());
    }
    provision = (int) (0.1 * sum);
    StringBuilder header = new StringBuilder();
    header.append("Säljredovisning för ")
      .append((historyFilteredseller == -1) ? "alla säljare" : "säljare " + historyFilteredseller)
      .append(".").append(NL).append(numberOfItems).append(" sålda varor ").append("för totalt ")
      .append(sum).append(" SEK.").append("\nRedovisningen omfattar följande betalningsmetoder: ")
      .append(historyFilteredPaymentOption).append("\ngenomförda innan ").append(LocalDateTime.now())
      .append('.');
    if (historyFilteredseller != -1) {
      header.append(NL).append("Provision: ").append(provision)
        .append(" Utbetalas säljare: ").append((sum - provision));

    }

    header.append(NL).append(NL).append(itemsDetailed);
    content.putString(header.toString());
    clipboard.setContent(content);
  }

  public void initUI() {
    //Set bold text on the current sum
    Font font = historyToBePayedLabel.getFont();
    historyToBePayedLabel.setFont(Font.font(null, FontWeight.BOLD, font.getSize()));

    payoutButton.setDisable(true);
    historyHidePayedOutItems.setSelected(false);
    historyPaymentChoice.getItems().clear();
    historyPaymentChoice.getItems().add("Alla");
    historyPaymentChoice.getItems().add(PaymentMethod.Swish.name());
    historyPaymentChoice.getItems().add(PaymentMethod.Kontant.name());
    historyPaymentChoice.getSelectionModel().select(0);
    historyHidePayedOutItems.selectedProperty().addListener((ov, old_val, new_val) -> {
      populateHistoryTable();
      toClipboardButton.setDisable(new_val);
    });
    historySellerChoice.getSelectionModel().selectedIndexProperty().addListener(new ChangeListener<Number>() {
      @Override
      public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        ReadOnlyIntegerProperty prop = (ReadOnlyIntegerProperty) observable;
        if (prop.get() == -1) {
          historyFilteredseller = -1;
        } else {
          String choice = historySellerChoice.getItems().get(prop.get());
          if ("Alla".equals(choice)) {
            historyFilteredseller = -1;

          } else {
            historyFilteredseller = Integer.parseInt(choice);
          }
        }
        populateHistoryTable();
      }
    });
    historyPaymentChoice.getSelectionModel().selectedIndexProperty().addListener(new ChangeListener<Number>() {
      //here
      @Override
      public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        ReadOnlyIntegerProperty prop = (ReadOnlyIntegerProperty) observable;
        if (prop.get() == -1) {
          //
        } else {
          String choice = historyPaymentChoice.getItems().get(prop.get());
          historyFilteredPaymentOption.clear();
          if (choice.equals(PaymentMethod.Kontant.name())) {
            historyFilteredPaymentOption.add(PaymentMethod.Kontant);
          } else if (choice.equals(PaymentMethod.Swish.name())) {
            historyFilteredPaymentOption.add(PaymentMethod.Swish);
          }

        }
        populateHistoryTable();
      }
    });

    String data = null;
    try {
      data = FileHelper.readFromFile();
      historyItems = historyTable.getItems();
      allHistoryItems = FormatHelper.toItems(data, true);
      Set<Integer> sellers = new TreeSet<Integer>();
      for (SoldItem item : allHistoryItems) {
        sellers.add(item.getSeller());
      }
      Iterator<Integer> it = sellers.iterator();
      historySellerChoice.getItems().clear();
      historySellerChoice.getItems().add("Alla");
      while (it.hasNext()) {
        historySellerChoice.getItems().add(String.valueOf(it.next()));
      }
      historySellerChoice.getSelectionModel().select(0);
      populateHistoryTable();
    } catch (IOException e) {
      e.printStackTrace();
    }

  }

  private void populateHistoryTable() {
    if (historyItems == null) {
      historyItems = historyTable.getItems();
    }
    historyItems.clear();
    if (historyFilteredPaymentOption.isEmpty()) {
      historyFilteredPaymentOption.add(PaymentMethod.Kontant);
      historyFilteredPaymentOption.add(PaymentMethod.Swish);
    }

    //historySellerChoice.getItems();


    boolean hidePayedOut = historyHidePayedOutItems.isSelected();
    for (SoldItem item : allHistoryItems) {
      if (historyFilteredseller == -1 || historyFilteredseller == item.getSeller()) {
        if (hidePayedOut && item.isCollectedBySeller()) {
          //collected, and we do not show collected -> no add

        } else if (historyFilteredPaymentOption.contains(PaymentMethod.Swish)
          && item.getPaymentMethod() == PaymentMethod.Swish) {
          historyItems.add(item);
        } else if (historyFilteredPaymentOption.contains(PaymentMethod.Kontant)
          && item.getPaymentMethod() == PaymentMethod.Kontant) {
          historyItems.add(item);
        }

      }
    }
    if (historyItems.isEmpty()) {
      for (PaymentMethod paymentMethod : historyFilteredPaymentOption) {
        System.out.println("paymentMethod : " + paymentMethod);

      }

    }
    for (SoldItem item : allHistoryItems) {
      System.out.println("item.getPaymentMethod(): " + item.getPaymentMethod());
    }
    updateHistoryLabels();
  }

  private void updateHistoryLabels() {
    int[] sums = getHistorySums(historyFilteredPaymentOption);
    int totSum = sums[0];
    int payedSum = sums[1];
    int totalPurchases = sums[2];
    int numOfItems = historyItems.size();
    int provision = (int) ((totSum - payedSum) * 0.1);
    int toBePayed = totSum - payedSum - provision;
    historyNoPurchaseLabel.setText("Antal köp: " + totalPurchases);
    historyNoItemsLabel.setText(numOfItems + " varor");
    historySumLabel.setText("Summa: " + totSum + " SEK");
    if (historyFilteredseller != -1 && historyFilteredPaymentOption.size() != 1) {
      historyProvisioningLabel.setText("Provision: " + provision + " SEK");
      historyToBePayedLabel.setText("Utbetalas: " + toBePayed + " SEK");
    } else {
      historyProvisioningLabel.setText("");
      historyToBePayedLabel.setText("");
    }
    payoutButton.setDisable(historyFilteredseller == -1
      || toBePayed == 0
      || historyFilteredPaymentOption.size() == 1);
  }

  /**
   * [0] price sum
   * [1] payed sum
   * [2] Number of purchases
   *
   * @return
   */
  private int[] getHistorySums(List<PaymentMethod> paymentMethods) {
    float sum = 0;
    float sumPayed = 0;
    Set<String> tmpPurchaseIdlist = new HashSet<>();
    for (SoldItem item : historyItems) {
      if (paymentMethods.contains(item.getPaymentMethod())) {
        sum += item.getPrice();
        if (item.isCollectedBySeller()) {
          sumPayed += item.getPrice();
        }
        tmpPurchaseIdlist.add(item.getPurchaseId());
      }
    }
    return new int[]{(int) sum, (int) sumPayed, tmpPurchaseIdlist.size()};
  }


}
