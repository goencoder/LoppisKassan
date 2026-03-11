package se.goencoder.loppiskassan.storage;

import org.json.JSONObject;
import se.goencoder.loppiskassan.V1PaymentMethod;

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
import java.util.logging.Logger;
import java.util.stream.Stream;

public class RejectedItemsStore {
    private static final Logger log = Logger.getLogger(RejectedItemsStore.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final String eventId;

    public RejectedItemsStore(String eventId) {
        this.eventId = eventId;
    }

    public List<RejectedItemEntry> readAll() throws IOException {
        Path path = LocalEventPaths.getRejectedPurchasesPath(eventId);
        if (Files.notExists(path)) {
            return List.of();
        }
        List<RejectedItemEntry> entries = new ArrayList<>();
        try (Stream<String> lines = Files.lines(path, StandardCharsets.UTF_8)) {
            lines.filter(line -> !line.isBlank()).forEach(line -> {
                try {
                    entries.add(fromJsonLine(line));
                } catch (Exception ex) {
                    log.warning("Failed to parse rejected item: " + ex.getMessage());
                }
            });
        } catch (RuntimeException ex) {
            throw new IOException("Failed to parse rejected items file: " + path, ex);
        }
        return entries;
    }

    public void appendAll(List<RejectedItemEntry> entries) throws IOException {
        if (entries == null || entries.isEmpty()) {
            return;
        }
        Path path = LocalEventPaths.getRejectedPurchasesPath(eventId);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            for (RejectedItemEntry entry : entries) {
                writer.write(toJsonLine(entry));
                writer.newLine();
            }
        }
    }

    public void saveAll(List<RejectedItemEntry> entries) throws IOException {
        Path path = LocalEventPaths.getRejectedPurchasesPath(eventId);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (BufferedWriter writer = Files.newBufferedWriter(
                path,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE)) {
            if (entries == null || entries.isEmpty()) {
                return;
            }
            for (RejectedItemEntry entry : entries) {
                writer.write(toJsonLine(entry));
                writer.newLine();
            }
        }
    }

    public int count() {
        try {
            return readAll().size();
        } catch (IOException e) {
            return 0;
        }
    }

    private static RejectedItemEntry fromJsonLine(String line) {
        JSONObject obj = new JSONObject(line);
        String timestamp = obj.optString("timestamp", "");
        String errorCode = obj.optString("errorCode", "");
        String reason = obj.optString("reason", "");

        JSONObject item = obj.optJSONObject("item");
        String itemId = null;
        String purchaseId = null;
        Integer seller = null;
        Integer price = null;
        V1PaymentMethod paymentMethod = null;
        LocalDateTime soldTime = null;

        if (item != null) {
            itemId = item.optString("itemId", item.optString("item_id", ""));
            purchaseId = item.optString("purchaseId", item.optString("purchase_id", ""));
            if (item.has("seller")) {
                seller = item.optInt("seller");
            }
            if (item.has("price")) {
                price = item.optInt("price");
            }
            paymentMethod = parsePaymentMethod(item.optString("paymentMethod", ""));
            soldTime = parseTime(item.optString("soldTime", item.optString("sold_time", "")));
        }

        return new RejectedItemEntry(
                itemId,
                purchaseId,
                seller,
                price,
                paymentMethod,
                soldTime,
                errorCode,
                reason,
                timestamp
        );
    }

    private static String toJsonLine(RejectedItemEntry entry) {
        JSONObject json = new JSONObject();
        json.put("timestamp", entry.getTimestamp());
        json.put("errorCode", entry.getErrorCode() != null ? entry.getErrorCode() : "");
        json.put("reason", entry.getReason() != null ? entry.getReason() : "");

        JSONObject item = new JSONObject();
        if (entry.getItemId() != null) {
            item.put("itemId", entry.getItemId());
        }
        if (entry.getPurchaseId() != null) {
            item.put("purchaseId", entry.getPurchaseId());
        }
        if (entry.getSeller() != null) {
            item.put("seller", entry.getSeller());
        }
        if (entry.getPrice() != null) {
            item.put("price", entry.getPrice());
        }
        if (entry.getPaymentMethod() != null) {
            item.put("paymentMethod", entry.getPaymentMethod().name());
        }
        if (entry.getSoldTime() != null) {
            item.put("soldTime", formatTime(entry.getSoldTime()));
        }
        json.put("item", item);

        return json.toString();
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
            if ("SWISH".equalsIgnoreCase(value)) {
                return V1PaymentMethod.Swish;
            }
            if ("KONTANT".equalsIgnoreCase(value) || "CASH".equalsIgnoreCase(value)) {
                return V1PaymentMethod.Kontant;
            }
            return V1PaymentMethod.Kontant;
        }
    }
}
