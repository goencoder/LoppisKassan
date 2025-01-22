package se.goencoder.loppiskassan.records;

import org.jetbrains.annotations.NotNull;
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
            + "säljare" + DELIMITER + "pris" + DELIMITER + "utbetalt" + DELIMITER + "Betalningsmetod" + DELIMITER + "uppladdad";
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
                    .append(item.getPaymentMethod().name()).append(DELIMITER)
                    .append(item.isUploaded() ? "true" : "false").append(LINE_ENDING);
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
            // Check if line is a comment or empty
            if (lines[i].startsWith("#") || lines[i].isEmpty()) {
                continue;
            }

            String[] columns = lines[i].split(DELIMITER);
            // columns:
            //   0 -> purchaseId
            //   1 -> itemId
            //   2 -> soldTime
            //   3 -> seller
            //   4 -> price
            //   5 -> collectedTime or "Nej"
            //   6 -> paymentMethod
            //   7 -> uploaded

            String collectedTime = columns[5];
            LocalDateTime dateTime = null;
            if (!collectedTime.equals("Nej")) {
                dateTime = stringToDateAndTime(collectedTime);
            }

            // Parse PaymentMethod from columns[6]
            PaymentMethod pm = PaymentMethod.valueOf(columns[6]);

            // Parse uploaded from columns[7] (if present)
            SoldItem item = getSoldItem(columns, dateTime, pm);
            items.add(item);
        }
        return items;
    }

    @NotNull
    private static SoldItem getSoldItem(String[] columns, LocalDateTime dateTime, PaymentMethod pm) {
        boolean uploaded = false;
        if (columns.length > 7) {
            uploaded = Boolean.parseBoolean(columns[7]);
        }

        // Construct new SoldItem
        // purchaseId
        // itemId
        // soldTime
        // seller
        // price
        // collectedBySellerTime
        // PaymentMethod
        // uploaded
        return new SoldItem(
                columns[0],                     // purchaseId
                columns[1],                     // itemId
                stringToDateAndTime(columns[2]),// soldTime
                Integer.parseInt(columns[3]),   // seller
                Integer.parseInt(columns[4]),   // price
                dateTime,                       // collectedBySellerTime
                pm,                             // PaymentMethod
                uploaded                        // uploaded
        );
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
