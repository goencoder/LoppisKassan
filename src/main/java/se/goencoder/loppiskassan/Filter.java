package se.goencoder.loppiskassan;

import java.util.function.Predicate;

public enum Filter {
    COLLECTED_BY_SELLER,
    PAYMENT_METHOD,
    SELLER;

    /**
     * getFilterFunc return a lambda filter function that can be used to filter a list of SoldItems.
     * <p>
     * Example;
     * if COLLECTED_BY_SELLER and "true" is passed as argument to getFilterFunc,
     * the lambda function will return true for all SoldItems that has been collected by the seller.
     * <p>
     * if PAYMENT_METHOD and "PaymentMethod.CASH" is passed as argument to getFilterFunc,
     * the lambda function will return true for all SoldItems that has been paid with cash.
     */
    public static FilterFunc getFilterFunc(Filter filter, Object value) {
        return switch (filter) {
            case COLLECTED_BY_SELLER -> item -> Boolean.parseBoolean(value.toString()) == item.isCollectedBySeller();
            case PAYMENT_METHOD -> item -> item.getPaymentMethod().name().equals(value);
            case SELLER -> item -> item.getSeller() == Integer.parseInt(value.toString());
        };
    }
    // Define FilterFunc as a functional interface using java.util.function.Predicate
    @FunctionalInterface
    public interface FilterFunc extends Predicate<SoldItem> {
        @Override
        boolean test(SoldItem item);
    }
}

