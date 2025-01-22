package se.goencoder.loppiskassan.utils;

import se.goencoder.loppiskassan.PaymentMethod;
import se.goencoder.loppiskassan.SoldItem;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SoldItemUtils {

    public static Set<String> getDistinctSellers(List<SoldItem> allHistoryItems) {
        return allHistoryItems.stream()
                .map(item -> String.valueOf(item.getSeller()))
                .collect(Collectors.toSet());
    }
    public static se.goencoder.iloppis.model.SoldItem toApiSoldItem(SoldItem item) {
        if (item == null) {
            return null;
        }
        se.goencoder.iloppis.model.SoldItem apiItem = new se.goencoder.iloppis.model.SoldItem();
        apiItem.setSeller(item.getSeller());
        apiItem.setItemId(item.getItemId());
        apiItem.setPrice(item.getPrice());
        apiItem.setPaymentMethod(
                item.getPaymentMethod() == PaymentMethod.Kontant
                        ? se.goencoder.iloppis.model.PaymentMethod.KONTANT
                        : se.goencoder.iloppis.model.PaymentMethod.SWISH
        );

        return apiItem;
    }
    public static SoldItem fromApiSoldItem(se.goencoder.iloppis.model.SoldItem apiSoldItem, boolean uploaded) {
        PaymentMethod paymentMethod = switch (apiSoldItem.getPaymentMethod()) {
            case se.goencoder.iloppis.model.PaymentMethod.KONTANT -> PaymentMethod.Kontant;
            case se.goencoder.iloppis.model.PaymentMethod.SWISH -> PaymentMethod.Swish;
            default -> throw new IllegalArgumentException("Unknown payment method: " + apiSoldItem.getPaymentMethod());
        };
        LocalDateTime collectedTime = null;
        if (apiSoldItem.getCollectedTime() != null) {
            collectedTime = apiSoldItem.getCollectedTime().toLocalDateTime();
        }
        return new SoldItem(apiSoldItem.getPurchaseId(),
                apiSoldItem.getItemId(),
                apiSoldItem.getSoldTime().toLocalDateTime(),
                apiSoldItem.getSeller(),
                apiSoldItem.getPrice(),
                collectedTime,
                paymentMethod,
                uploaded
        );
    }

}

