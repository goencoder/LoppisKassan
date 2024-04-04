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
public class SoldItem {
    private final int seller;
    private final int price;
    private LocalDateTime collectedBySellerTime;
    private LocalDateTime soldTime;
    private PaymentMethod paymentMethod;
    private String purchaseId;
    private final String itemId;

    /**
     * Konstruktor för att skapa en ny såld vara.
     */
    public SoldItem(String purchaseId, String itemId, LocalDateTime soldTime, int seller, int price,
                    LocalDateTime collectedBysellerTime, PaymentMethod paymentMethod) {
        this.purchaseId = purchaseId;
        this.itemId = itemId;
        this.price = price;
        this.seller = seller;
        this.soldTime = soldTime;
        this.collectedBySellerTime = collectedBysellerTime;
        this.paymentMethod = paymentMethod;
    }

    /**
     * Konstruktor som skapar en såld vara med en genererad unik identifierare och aktuell tidpunkt som säljtid.
     */
    public SoldItem(int seller, int price, PaymentMethod paymentMethod) {
        this(UUID.randomUUID().toString(), UUID.randomUUID().toString(), LocalDateTime.now(), seller, price, null, paymentMethod);
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

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
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

    // Overridden methods for equality and hashing

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SoldItem soldItem = (SoldItem) o;
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

