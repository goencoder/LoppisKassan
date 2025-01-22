package se.goencoder.loppiskassan.utils;

import se.goencoder.loppiskassan.Filter;
import se.goencoder.loppiskassan.PaymentMethod;
import se.goencoder.loppiskassan.SoldItem;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class SoldItemUtils {

    public static Set<String> getDistinctSellers(List<SoldItem> allHistoryItems) {
        return allHistoryItems.stream()
                .map(item -> String.valueOf(item.getSeller()))
                .collect(Collectors.toSet());
    }
    public static se.goencoder.iloppis.model.SoldItem toApiSoldItem(SoldItem item) {
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
}

