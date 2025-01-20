package se.goencoder.loppiskassan;

import se.goencoder.loppiskassan.records.FileHelper;
import se.goencoder.loppiskassan.records.FormatHelper;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static se.goencoder.loppiskassan.records.FileHelper.LOPPISKASSAN_CSV;

/**
 * Created by gengdahl on 2016-09-20.
 */
public class TestRunner {
    private final Random random = new Random(System.currentTimeMillis());
    public static void main(String[] args){
        TestRunner tr = new TestRunner();
        for (int i = 0; i < 1000; i++) {
            tr.testManyEntries();
        }

    }
    public void testManyEntries(){
        int numberOfCustomers = 10;
        String purchaseId;
        PaymentMethod paymentMethod = PaymentMethod.Swish;

        for (int i = 0; i < numberOfCustomers; i++){
            switch (paymentMethod) {
                case Swish: paymentMethod = PaymentMethod.Kontant; break;
                case Kontant: paymentMethod = PaymentMethod.Swish; break;
                default: break;
            }
            purchaseId = UUID.randomUUID().toString();
            try {
                FileHelper.saveToFile(LOPPISKASSAN_CSV,
                        "",
                        FormatHelper.toCVS(createRandomSoldItems(paymentMethod,
                  purchaseId)));
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
    private List<SoldItem> createRandomSoldItems(PaymentMethod paymentMethod,
                                                 String purchaseId){
        int numberOfRecords = random.nextInt(19) + 1;
        List<SoldItem> items = new ArrayList<>(numberOfRecords);
        for (int i = 0 ; i < numberOfRecords; i++){
            SoldItem item = createRandomSoldItem(paymentMethod, purchaseId);
            item.setSoldTime(LocalDateTime.now());
            items.add(item);

        }
        return items;
    }
    private SoldItem createRandomSoldItem(PaymentMethod paymentMethod,
                                          String purchaseId){
        int seller = Math.abs(random.nextInt() % 9) + 1;
        int price = Math.abs(random.nextInt() % 20) +1 ;

        return new SoldItem(purchaseId,
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                seller,
                price,
                null,
                paymentMethod,
                false);
    }

}
