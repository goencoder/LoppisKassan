package se.teddy.loppiskassan;


import javafx.beans.property.*;
import se.teddy.loppiskassan.records.FormatHelper;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Created by gengdahl on 2016-08-16.
 */
public class SoldItem {
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

    public String getItemId() {
        return itemId;
    }

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
    @Override
    public int hashCode(){
        return Objects.hash(itemId);
    }


}
