package se.goencoder.loppiskassan.storage;

import org.json.JSONObject;
import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
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
        fsync(path);
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

    public static List<V1SoldItem> readLastItems(Path path, int maxLines) throws IOException {
        if (Files.notExists(path) || maxLines <= 0) {
            return List.of();
        }

        List<String> lines = readLastNonBlankLines(path, maxLines);
        List<V1SoldItem> items = new ArrayList<>(lines.size());
        try {
            for (String line : lines) {
                items.add(fromJsonLine(line));
            }
        } catch (RuntimeException ex) {
            throw new IOException("Failed to parse JSONL file: " + path, ex);
        }
        return items;
    }

    public static void writeItems(Path path, List<V1SoldItem> items) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Path tempPath = path.resolveSibling(path.getFileName() + ".tmp");
        boolean moved = false;
        try {
            // Write to temp file, fsync, then move into place. This reduces the risk of partial
            // writes/truncation, but does not by itself guarantee the rename is durable on crash.
            try (BufferedWriter writer = Files.newBufferedWriter(
                    tempPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE)) {
                if (items != null) {
                    for (V1SoldItem item : items) {
                        writer.write(toJsonLine(item));
                        writer.newLine();
                    }
                }
            }
            fsync(tempPath);
            try {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(tempPath, path, StandardCopyOption.REPLACE_EXISTING);
            }
            fsync(path);
            fsyncParentDirectoryBestEffort(path);
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(tempPath);
            }
        }
    }

    /**
     * Force data to durable storage via fsync.
     */
    private static void fsync(Path path) throws IOException {
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void fsyncParentDirectoryBestEffort(Path path) {
        Path parent = path.getParent();
        if (parent == null) {
            return;
        }
        try (FileChannel channel = FileChannel.open(parent, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (Exception ignored) {
            // Directory fsync is not supported on all platforms/filesystems.
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
        if (item.getRegisterId() != null) {
            json.put("registerId", item.getRegisterId());
        }
        if (item.getSessionId() != null) {
            json.put("sessionId", item.getSessionId());
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
        V1SoldItem item = new V1SoldItem(
                purchaseId,
                itemId,
                soldTime,
                seller,
                price,
                collected,
                paymentMethod,
                uploaded
        );
        String registerId = obj.optString("registerId", obj.optString("register_id", ""));
        if (!registerId.isBlank()) {
            item.setRegisterId(registerId);
        }
        String sessionId = obj.optString("sessionId", obj.optString("session_id", ""));
        if (!sessionId.isBlank()) {
            item.setSessionId(sessionId);
        }
        return item;
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

    private static List<String> readLastNonBlankLines(Path path, int maxLines) throws IOException {
        List<String> lines = new ArrayList<>(maxLines);
        try (var file = new java.io.RandomAccessFile(path.toFile(), "r")) {
            long pointer = file.length() - 1;
            var currentLine = new java.io.ByteArrayOutputStream();

            while (pointer >= 0 && lines.size() < maxLines) {
                file.seek(pointer);
                int value = file.read();
                if (value == '\n') {
                    addDecodedLine(lines, currentLine);
                } else if (value != '\r') {
                    currentLine.write(value);
                }
                pointer--;
            }
            addDecodedLine(lines, currentLine);
        }
        Collections.reverse(lines);
        return lines;
    }

    private static void addDecodedLine(List<String> lines, java.io.ByteArrayOutputStream currentLine) {
        if (currentLine.size() == 0) {
            return;
        }
        byte[] bytes = currentLine.toByteArray();
        reverse(bytes);
        String line = new String(bytes, StandardCharsets.UTF_8);
        if (!line.isBlank()) {
            lines.add(line);
        }
        currentLine.reset();
    }

    private static void reverse(byte[] bytes) {
        for (int left = 0, right = bytes.length - 1; left < right; left++, right--) {
            byte temp = bytes[left];
            bytes[left] = bytes[right];
            bytes[right] = temp;
        }
    }
}
