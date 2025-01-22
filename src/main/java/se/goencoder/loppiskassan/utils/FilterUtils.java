package se.goencoder.loppiskassan.utils;

import se.goencoder.loppiskassan.Filter;
import se.goencoder.loppiskassan.SoldItem;

import java.util.List;
import java.util.stream.Collectors;

public class FilterUtils {
    public static List<SoldItem> applyFilters(
            List<SoldItem> items, String paidFilter, String sellerFilter, String paymentMethodFilter) {
        return items.stream()
                .filter(item -> switch (paidFilter) {
                    case "Ja" -> item.isCollectedBySeller();
                    case "Nej" -> !item.isCollectedBySeller();
                    default -> true;
                })
                .filter(item -> sellerFilter == null || sellerFilter.equals("Alla")
                        || Filter.getFilterFunc(Filter.SELLER, sellerFilter).test(item))
                .filter(item -> paymentMethodFilter == null || paymentMethodFilter.equals("Alla")
                        || Filter.getFilterFunc(Filter.PAYMENT_METHOD, paymentMethodFilter).test(item))
                .collect(Collectors.toList());
    }
}

