package se.goencoder.loppiskassan.utils;

import se.goencoder.loppiskassan.Filter;
import se.goencoder.loppiskassan.V1SoldItem;

import java.util.List;
import java.util.stream.Collectors;

public class FilterUtils {
    public static List<V1SoldItem> applyFilters(
            List<V1SoldItem> items, String paidFilter, String sellerFilter, String paymentMethodFilter) {
        // Handle null items list by returning empty list
        if (items == null) {
            return List.of();
        }

        return items.stream()
                .filter(item -> paidFilter == null ||
                        Boolean.parseBoolean(paidFilter) == item.isCollectedBySeller())
                .filter(item -> sellerFilter == null
                        || Filter.getFilterFunc(Filter.SELLER, sellerFilter).test(item))
                .filter(item -> paymentMethodFilter == null
                        || Filter.getFilterFunc(Filter.PAYMENT_METHOD, paymentMethodFilter).test(item))
                .collect(Collectors.toList());
    }
}
