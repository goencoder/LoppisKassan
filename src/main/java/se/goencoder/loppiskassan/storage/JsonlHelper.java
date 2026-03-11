package se.goencoder.loppiskassan.storage;

import org.json.JSONObject;
import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public final class JsonlHelper {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private JsonlHelper() {}

    public static void appendItems(Path path, List<V1SoldItem> items) throws IOException {
        if (items == null || items.isEmpty()) {
            return;
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            for (V1SoldItem item : items) {
                writer.write(toJsonLine(item));
                writer.newLine();
            }
        }
    }

    public static List<V1SoldItem> readItems(Path path) throws IOException {
        if (Files.notExists(path)) {
            return List.of();
        }
        List<V1SoldItem> items = new ArrayList<>();
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            lines.filter(line -> !line.isBlank())
                    .forEach(line -> items.add(fromJsonLine(line)));
        } catch (RuntimeException ex) {
            throw new IOException("Failed to parse JSONL file: " + path, ex);
        }
        return items;
    }

    public static void writeItems(Path path, List<V1SoldItem> items) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            if (items == null || items.isEmpty()) {
                return;
            }
            for (V1SoldItem item : items) {
                writer.write(toJsonLine(item));
                writer.newLine();
            }
        }
    }

    public static String toJsonLine(V1SoldItem item) {
        JSONObject json = new JSONObject();
        json.put("itemId", item.getItemId());
        json.put("purchaseId", item.getPurchaseId());
        json.put("seller", item.getSeller());
        json.put("price", item.getPrice());
        if (item.getPaymentMethod() != null) {
            json.put("paymentMethod", item.getPaymentMethod().name());
        }
        json.put("soldTime", formatTime(item.getSoldTime()));
        if (item.getCollectedBySellerTime() != null) {
            json.put("paidOutTime", formatTime(item.getCollectedBySellerTime()));
        }
        json.put("uploaded", item.isUploaded());
        return json.toString();
    }

    public static V1SoldItem fromJsonLine(String line) {
        JSONObject obj = new JSONObject(line);
        String purchaseId = obj.optString("purchaseId", obj.optString("purchase_id", ""));
        String itemId = obj.optString("itemId", obj.optString("item_id", ""));
        LocalDateTime soldTime = parseTime(obj.optString("soldTime", obj.optString("sold_time", "")));
        int seller = obj.optInt("seller", 0);
        int price = obj.optInt("price", 0);
        String paidOutTime = obj.optString("paidOutTime", obj.optString("collectedBySellerTime", ""));
        LocalDateTime collected = parseTime(paidOutTime);
        V1PaymentMethod paymentMethod = parsePaymentMethod(obj.optString("paymentMethod", ""));
        boolean uploaded = obj.optBoolean("uploaded", false);
        if (soldTime == null) {
            soldTime = LocalDateTime.now();
        }
        return new V1SoldItem(
                purchaseId,
                itemId,
                soldTime,
                seller,
                price,
                collected,
                paymentMethod,
                uploaded
        );
    }

    private static String formatTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "";
        }
        return dateTime.atOffset(ZoneOffset.UTC).format(DATE_FORMATTER);
    }

    private static LocalDateTime parseTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value, DATE_FORMATTER).toLocalDateTime();
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private static V1PaymentMethod parsePaymentMethod(String value) {
        if (value == null || value.isBlank()) {
            return V1PaymentMethod.Kontant;
        }
        try {
            return V1PaymentMethod.valueOf(value);
        } catch (IllegalArgumentException ex) {
            return V1PaymentMethod.Kontant;
        }
    }
}
