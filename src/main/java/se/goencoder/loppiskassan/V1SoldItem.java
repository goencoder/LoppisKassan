package se.goencoder.loppiskassan;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Created by gengdahl on 2016-08-16.
 * Representerar en såld vara inom Loppiskassan-systemet.
 * <p>
 * Varje såld vara har en unik identifierare, tidpunkt då den såldes, pris, betalningsmetod m.m.
 * </p>
 */
public class V1SoldItem {
    private final int seller;
    private final int price;
    private LocalDateTime collectedBySellerTime;
    private LocalDateTime soldTime;
    private V1PaymentMethod paymentMethod;
    private String purchaseId;
    private final String itemId;
    private boolean uploaded;

    /**
     * Konstruktor för att skapa en ny såld vara.
     */
    public V1SoldItem(
            String purchaseId,
            String itemId,
            LocalDateTime soldTime,
            int seller,
            int price,
            LocalDateTime collectedBysellerTime,
            V1PaymentMethod paymentMethod,
            boolean uploaded) {
        this.purchaseId = purchaseId;
        this.itemId = itemId;
        this.price = price;
        this.seller = seller;
        this.soldTime = soldTime;
        this.collectedBySellerTime = collectedBysellerTime;
        this.paymentMethod = paymentMethod;
        this.uploaded = uploaded;
    }

    /**
     * Konstruktor som skapar en såld vara med en genererad unik identifierare och aktuell tidpunkt som säljtid.
     */
    public V1SoldItem(int seller, int price, V1PaymentMethod paymentMethod) {
        this(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                seller,
                price,
                null,
                paymentMethod,
                false);
    }

    // Standard getters and setters

    public String getItemId() {
        return itemId;
    }

    public int getSeller() {
        return seller;
    }

    public int getPrice() {
        return price;
    }

    public V1PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(V1PaymentMethod paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public LocalDateTime getCollectedBySellerTime() {
        return collectedBySellerTime;
    }

    public void setCollectedBySellerTime(LocalDateTime dateTime) {
        this.collectedBySellerTime = dateTime;
    }

    public LocalDateTime getSoldTime() {
        return soldTime;
    }

    public void setSoldTime(LocalDateTime dateTime) {
        this.soldTime = dateTime;
    }


    public String getPurchaseId() {
        return purchaseId;
    }

    public void setPurchaseId(String purchaseId) {
        this.purchaseId = purchaseId;
    }

    public boolean isUploaded() {
        return uploaded;
    }
    public void setUploaded(boolean uploaded) {
        this.uploaded = uploaded;
    }

    // Overridden methods for equality and hashing

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        V1SoldItem soldItem = (V1SoldItem) o;
        return itemId.equals(soldItem.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId);
    }

    public boolean isCollectedBySeller() {
        return collectedBySellerTime != null;
    }


}

