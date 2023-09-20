package se.teddy.loppiskassan.records;

import se.teddy.loppiskassan.PaymentMethod;
import se.teddy.loppiskassan.SoldItem;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Hjälpklass för att formatera data mellan CSV-strängar och listor med sålda objekt.
 *
 * @author gengdahl
 * @since 2016-08-18
 */
public class FormatHelper {
    public static final String LINE_ENDING= System.lineSeparator();
    private static final String DELIMITER=",";
    public static String CVS_HEADERS="Köp-id" + DELIMITER + "Varu-id" + DELIMITER + "tidsstämpel"+DELIMITER
      +"säljare"+DELIMITER+"pris"+DELIMITER+"utbetalt"+DELIMITER+"Betalningsmetod";
    private static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * Konverterar en lista av sålda objekt till en CSV-sträng.
     *
     * @param items Lista av sålda objekt.
     * @return En CSV-formaterad sträng av sålda objekt.
     */
    public static String toCVS(List<SoldItem> items) {
        //timestamp, seller, price, paymentType
        StringBuilder stringBuilder = new StringBuilder();
        for (SoldItem item : items) {
            String collectedTime = "Nej";
            if (item.isCollectedBySeller()) {
                collectedTime = item.getCollectedBySellerTime();

            }
            stringBuilder.append(item.getPurchaseId()).append(DELIMITER)
              .append(item.getItemId()).append(DELIMITER)
              .append(item.getSoldTime().toString()).append(DELIMITER)
              .append(item.getSeller()).append(DELIMITER)
              .append(item.getPrice()).append(DELIMITER)
              .append(collectedTime).append(DELIMITER)
              .append(item.getPaymentMethod().name()).append(LINE_ENDING);

        }
        return stringBuilder.toString();
    }

    /**
     * Konverterar en CSV-sträng till en lista av sålda objekt.
     *
     * @param CSV CSV-sträng med sålda objekt.
     * @param withHeaderLine Om strängen inkluderar en huvudrad.
     * @return En lista av sålda objekt extraherad från CSV-strängen.
     */
    public static List<SoldItem> toItems(String CSV, boolean withHeaderLine){
        String[] lines = CSV.split(LINE_ENDING);
        int startIndex=0;
        if (withHeaderLine){
            startIndex=1;
        }
        List<SoldItem> items = new ArrayList<SoldItem>(lines.length - startIndex);
        for (int i = startIndex; i < lines.length; i++){
            String[] columns = lines[i].split(DELIMITER);
            String collectedTime = columns[5];
            LocalDateTime dateTime = null;
            if (!collectedTime.equals("Nej")){
                dateTime = stringToDateAndTime(collectedTime);
            }
            items.add(new SoldItem(columns[0],
              columns[1],
              stringToDateAndTime(columns[2]),
              Integer.parseInt(columns[3]),
              Float.parseFloat(columns[4]),
              dateTime,
              PaymentMethod.valueOf(columns[6])));
        }
        return items;

    }
    /**
     * Konverterar en LocalDateTime till en sträng.
     *
     * @param dateTime LocalDateTime-objekt som ska konverteras.
     * @return En strängrepresentation av datumet och tiden.
     */
    public static String dateAndTimeToString(LocalDateTime dateTime){
        if (dateTime == null){
            return "";
        }
        return dateTime.format(formatter);

    }
    /**
     * Konverterar en sträng till en LocalDateTime.
     *
     * @param dateText Strängrepresentation av datum och tid.
     * @return LocalDateTime-objekt baserat på den angivna strängen.
     */
    public static LocalDateTime stringToDateAndTime(String dateText){
        return LocalDateTime.parse(dateText,formatter);

    }
}
