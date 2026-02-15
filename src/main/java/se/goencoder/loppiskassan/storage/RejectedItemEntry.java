package se.goencoder.loppiskassan.storage;

import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;

import java.time.LocalDateTime;

public class RejectedItemEntry {
    private final String itemId;
    private final String purchaseId;
    private final Integer seller;
    private final Integer price;
    private final V1PaymentMethod paymentMethod;
    private final LocalDateTime soldTime;
    private final String errorCode;
    private final String reason;
    private final String timestamp;

    public RejectedItemEntry(
            String itemId,
            String purchaseId,
            Integer seller,
            Integer price,
            V1PaymentMethod paymentMethod,
            LocalDateTime soldTime,
            String errorCode,
            String reason,
            String timestamp
    ) {
        this.itemId = itemId;
        this.purchaseId = purchaseId;
        this.seller = seller;
        this.price = price;
        this.paymentMethod = paymentMethod;
        this.soldTime = soldTime;
        this.errorCode = errorCode;
        this.reason = reason;
        this.timestamp = timestamp;
    }

    public String getItemId() {
        return itemId;
    }

    public String getPurchaseId() {
        return purchaseId;
    }

    public Integer getSeller() {
        return seller;
    }

    public Integer getPrice() {
        return price;
    }

    public V1PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public LocalDateTime getSoldTime() {
        return soldTime;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getReason() {
        return reason;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public boolean canRequeue() {
        return itemId != null && !itemId.isBlank()
                && purchaseId != null && !purchaseId.isBlank()
                && soldTime != null
                && seller != null
                && price != null;
    }

    public V1SoldItem toSoldItem() {
        if (!canRequeue()) {
            return null;
        }
        return new V1SoldItem(
                purchaseId,
                itemId,
                soldTime,
                seller,
                price,
                null,
                paymentMethod,
                false
        );
    }
}
