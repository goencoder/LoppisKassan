package se.goencoder.loppiskassan.utils;

import org.junit.jupiter.api.Test;
import se.goencoder.iloppis.model.V1PaymentMethod;

import static org.junit.jupiter.api.Assertions.assertThrows;

class SoldItemUtilsTest {

    @Test
    void fromApiSoldItemRejectsUnspecifiedPaymentMethod() {
        se.goencoder.iloppis.model.V1SoldItem apiItem = new se.goencoder.iloppis.model.V1SoldItem();
        apiItem.setPaymentMethod(V1PaymentMethod.PAYMENT_METHOD_UNSPECIFIED);

        assertThrows(IllegalArgumentException.class, () -> SoldItemUtils.fromApiSoldItem(apiItem, true));
    }
}
