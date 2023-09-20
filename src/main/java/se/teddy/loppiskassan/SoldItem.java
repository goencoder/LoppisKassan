package se.teddy.loppiskassan;


import javafx.beans.property.*;
import se.teddy.loppiskassan.records.FormatHelper;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Created by gengdahl on 2016-08-16.
 */
/**
 * Representerar en såld vara inom Loppiskassan-systemet.
 * <p>
 * Varje såld vara har en unik identifierare, tidpunkt då den såldes, pris, betalningsmetod m.m.
 * </p>
 *
 * @author gengdahl
 * @since 2016-08-16
 */
public class SoldItem {
    /**
     * Konstruktor för att skapa en ny såld vara.
     */
    public SoldItem(String purchaseId, String itemId, LocalDateTime soldTime, int seller, float price,
                    LocalDateTime collectedBysellerTime, PaymentMethod paymentMethod){
        setPurchaseId(purchaseId);
        setItemId(itemId);
        setPrice(price);
        setSeller(seller);
        setSoldTime(soldTime);
        setCollectedBySellerTime(collectedBysellerTime);
        setPaymentMethod(paymentMethod);

    }
    /**
     * Konstruktor som skapar en såld vara med en genererad unik identifierare och aktuell tidpunkt som säljtid.
     */
    public SoldItem(int seller, float price, PaymentMethod paymentMethod){
        this(null, UUID.randomUUID().toString(), LocalDateTime.now(), seller, price, null, paymentMethod);
    }

    private SimpleIntegerProperty seller = new SimpleIntegerProperty(0);
    private SimpleFloatProperty price = new SimpleFloatProperty(0);
    private SimpleObjectProperty<LocalDateTime> collectedBySellerTime = new SimpleObjectProperty<LocalDateTime>(null);
    private SimpleObjectProperty<LocalDateTime> soldTime = new SimpleObjectProperty<LocalDateTime>(null);
    private SimpleObjectProperty<PaymentMethod> paymentMethod = new SimpleObjectProperty<PaymentMethod>(null);
    private String purchaseId = null;
    private String itemId = null;

    /**
     * Returnerar den unika identifieraren för varan.
     *
     * @return Varans identifierare.
     */
    public String getItemId() {
        return itemId;
    }

    /**
     * Ställer in den unika identifieraren för varan.
     *
     * @param itemId Den unika identifieraren för varan.
     */
    public void setItemId(String itemId) {
        this.itemId = itemId;
    }

    public int getSeller() {
        return seller.get();
    }

    public SimpleIntegerProperty sellerProperty() {
        return seller;
    }

    public void setSeller(int seller) {
        this.seller.set(seller);
    }

    public float getPrice() {
        return price.get();
    }

    public SimpleFloatProperty priceProperty() {
        return price;
    }

    public void setPrice(float price) {
        this.price.set(price);
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod.get();
    }

    public void setPaymentMethod(PaymentMethod paymentMethod) {
        this.paymentMethod.set(paymentMethod);
    }

    public void setCollectedBySellerTime(LocalDateTime dateTime){
        this.collectedBySellerTime.set(dateTime);
    }
    public String getCollectedBySellerTime(){
        return FormatHelper.dateAndTimeToString(this.collectedBySellerTime.getValue());
    }
    public void setSoldTime(LocalDateTime dateTime){
        this.soldTime.setValue(dateTime);
    }
    public String getSoldTime(){
        return FormatHelper.dateAndTimeToString(this.soldTime.getValue());
    }

    public boolean isCollectedBySeller(){
        return collectedBySellerTime.getValue() != null;
    }
    public void setPurchaseId(String purchaseId){
        this.purchaseId = purchaseId;

    }
    public String getPurchaseId(){
        return purchaseId;

    }
    /**
     * Kontrollerar om två sålda varor är lika baserat på deras unika identifierare.
     *
     * @param o Objektet att jämföra med.
     * @return {@code true} om objekten representerar samma vara, annars {@code false}.
     */
    @Override
    public boolean equals(Object o){
        if (o == this){
            return true;

        }
        if (!(o instanceof SoldItem)){
            return false;
        }
        SoldItem other = (SoldItem)o;
        return Objects.equals(itemId, other.getItemId());
    }
    /**
     * Returnerar en hashkod för den sålda varan baserad på dess unika identifierare.
     *
     * @return Hashkoden för den sålda varan.
     */
    @Override
    public int hashCode(){
        return Objects.hash(itemId);
    }


}
