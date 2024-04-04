package se.goencoder.loppiskassan.records;

import se.goencoder.loppiskassan.PaymentMethod;
import se.goencoder.loppiskassan.SoldItem;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class FormatHelper {
    public static final String LINE_ENDING = System.lineSeparator();
    private static final String DELIMITER = ",";
    public static final String CVS_HEADERS = "Köp-id" + DELIMITER + "Varu-id" + DELIMITER + "tidsstämpel" + DELIMITER
            + "säljare" + DELIMITER + "pris" + DELIMITER + "utbetalt" + DELIMITER + "Betalningsmetod";
    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static String toCVS(List<SoldItem> items) {
        StringBuilder stringBuilder = new StringBuilder();
        for (SoldItem item : items) {
            String collectedTime = "Nej";
            if (item.isCollectedBySeller()) {
                collectedTime = dateAndTimeToString(item.getCollectedBySellerTime());
            }
            stringBuilder.append(item.getPurchaseId()).append(DELIMITER)
                    .append(item.getItemId()).append(DELIMITER)
                    .append(dateAndTimeToString(item.getSoldTime())).append(DELIMITER)
                    .append(item.getSeller()).append(DELIMITER)
                    .append(item.getPrice()).append(DELIMITER)
                    .append(collectedTime).append(DELIMITER)
                    .append(item.getPaymentMethod().name()).append(LINE_ENDING);
        }
        return stringBuilder.toString();
    }

    public static List<SoldItem> toItems(String CSV, boolean withHeaderLine) {
        String[] lines = CSV.split(LINE_ENDING);
        int startIndex = 0;
        if (withHeaderLine) {
            startIndex = 1;
        }
        List<SoldItem> items = new ArrayList<>(lines.length - startIndex);
        for (int i = startIndex; i < lines.length; i++) {
            String[] columns = lines[i].split(DELIMITER);
            String collectedTime = columns[5];
            LocalDateTime dateTime = null;
            if (!collectedTime.equals("Nej")) {
                dateTime = stringToDateAndTime(collectedTime);
            }
            items.add(new SoldItem(columns[0],
                    columns[1],
                    stringToDateAndTime(columns[2]),
                    Integer.parseInt(columns[3]),
                    Integer.parseInt(columns[4]),
                    dateTime,
                    PaymentMethod.valueOf(columns[6])));
        }
        return items;
    }

    public static String dateAndTimeToString(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.format(formatter);
    }

    public static LocalDateTime stringToDateAndTime(String dateText) {
        return LocalDateTime.parse(dateText, formatter);
    }
}
