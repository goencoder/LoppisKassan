package se.goencoder.loppiskassan.utils;

import se.goencoder.loppiskassan.V1SoldItem;

import java.util.ArrayList;
import java.util.List;

public class FilterUtils {
        public record FilterResult(List<V1SoldItem> items, int totalSum) {}

    public static List<V1SoldItem> applyFilters(
            List<V1SoldItem> items, String paidFilter, String sellerFilter, String paymentMethodFilter) {
                return applyFiltersWithSum(items, paidFilter, sellerFilter, paymentMethodFilter).items();
        }

        public static FilterResult applyFiltersWithSum(
                        List<V1SoldItem> items, String paidFilter, String sellerFilter, String paymentMethodFilter) {
                if (items == null || items.isEmpty()) {
                        return new FilterResult(List.of(), 0);
                }

                Boolean paid = paidFilter == null ? null : Boolean.parseBoolean(paidFilter);
                Integer seller = sellerFilter == null ? null : Integer.parseInt(sellerFilter);
                String payment = paymentMethodFilter == null ? null : paymentMethodFilter;

                List<V1SoldItem> filtered = new ArrayList<>(items.size());
                int total = 0;

                for (V1SoldItem item : items) {
                        if (paid != null && paid != item.isCollectedBySeller()) {
                                continue;
                        }
                        if (seller != null && item.getSeller() != seller) {
                                continue;
                        }
                        if (payment != null) {
                                var method = item.getPaymentMethod();
                                if (method == null || !method.name().equals(payment)) {
                                        continue;
                                }
                        }

                        filtered.add(item);
                        total += item.getPrice();
                }

                return new FilterResult(filtered, total);
    }
}
