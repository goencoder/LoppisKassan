package se.goencoder.loppiskassan.utils;

import org.junit.jupiter.api.Test;
import se.goencoder.iloppis.model.V1PaymentMethod;
import se.goencoder.loppiskassan.V1SoldItem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SoldItemUtilsTest {

    @Test
    void toApiSoldItemPreservesZeroPrice() {
        V1SoldItem item = new V1SoldItem(1, 0, se.goencoder.loppiskassan.V1PaymentMethod.Kontant);

        se.goencoder.iloppis.model.V1SoldItem apiItem = SoldItemUtils.toApiSoldItem(item);

        assertEquals(0, apiItem.getPrice());
    }

    @Test
    void fromApiSoldItemRejectsUnspecifiedPaymentMethod() {
        se.goencoder.iloppis.model.V1SoldItem apiItem = new se.goencoder.iloppis.model.V1SoldItem();
        apiItem.setPaymentMethod(V1PaymentMethod.PAYMENT_METHOD_UNSPECIFIED);

        assertThrows(IllegalArgumentException.class, () -> SoldItemUtils.fromApiSoldItem(apiItem, true));
    }
}
