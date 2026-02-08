package se.goencoder.loppiskassan.utils;

import se.goencoder.loppiskassan.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class SoldItemUtils {

    public static Set<String> getDistinctSellers(List<V1SoldItem> allHistoryItems) {
        return allHistoryItems.stream()
                .map(item -> String.valueOf(item.getSeller()))
                .collect(Collectors.toSet());
    }
    public static se.goencoder.iloppis.model.V1SoldItem toApiSoldItem(V1SoldItem item) {
        if (item == null) {
            return null;
        }
        se.goencoder.iloppis.model.V1SoldItem apiItem = new se.goencoder.iloppis.model.V1SoldItem();
        apiItem.setSeller(item.getSeller());
        apiItem.setPrice(item.getPrice());
        apiItem.setPaymentMethod(
                item.getPaymentMethod() == V1PaymentMethod.Kontant
                        ? se.goencoder.iloppis.model.V1PaymentMethod.KONTANT
                        : se.goencoder.iloppis.model.V1PaymentMethod.SWISH
        );
        apiItem.setPurchaseId(item.getPurchaseId());

        return apiItem;
    }
    public static V1SoldItem fromApiSoldItem(se.goencoder.iloppis.model.V1SoldItem apiSoldItem, boolean uploaded) {
        V1PaymentMethod paymentMethod = switch (apiSoldItem.getPaymentMethod()) {
            case se.goencoder.iloppis.model.V1PaymentMethod.KONTANT -> V1PaymentMethod.Kontant;
            case se.goencoder.iloppis.model.V1PaymentMethod.SWISH -> V1PaymentMethod.Swish;
            default -> throw new IllegalArgumentException("Unknown payment method: " + apiSoldItem.getPaymentMethod());
        };
        LocalDateTime collectedTime = null;
        if (apiSoldItem.getCollectedTime() != null) {
            collectedTime = apiSoldItem.getCollectedTime().toLocalDateTime();
        }
        return new V1SoldItem(apiSoldItem.getPurchaseId(),
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
