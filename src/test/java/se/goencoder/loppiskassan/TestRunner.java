package se.goencoder.loppiskassan;

import se.goencoder.loppiskassan.records.FileHelper;
import se.goencoder.loppiskassan.records.FormatHelper;
import se.goencoder.loppiskassan.utils.UlidGenerator;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import static se.goencoder.loppiskassan.records.FileHelper.LOPPISKASSAN_CSV;

/**
 * Created by gengdahl on 2016-09-20.
 */
public class TestRunner {
    private static final Logger logger = Logger.getLogger(TestRunner.class.getName());

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
        V1PaymentMethod paymentMethod = V1PaymentMethod.Swish;

        for (int i = 0; i < numberOfCustomers; i++){
            switch (paymentMethod) {
                case Swish: paymentMethod = V1PaymentMethod.Kontant; break;
                case Kontant: paymentMethod = V1PaymentMethod.Swish; break;
                default: break;
            }
            purchaseId = UlidGenerator.generate();
            try {
                FileHelper.saveToFile(LOPPISKASSAN_CSV,
                        "",
                        FormatHelper.toCVS(createRandomSoldItems(paymentMethod,
                  purchaseId)));
            } catch (IOException e) {
                logger.log(Level.SEVERE, "Failed to save to file", e);
            }

        }
    }
    private List<V1SoldItem> createRandomSoldItems(V1PaymentMethod paymentMethod,
                                                 String purchaseId){
        int numberOfRecords = random.nextInt(19) + 1;
        List<V1SoldItem> items = new ArrayList<>(numberOfRecords);
        for (int i = 0 ; i < numberOfRecords; i++){
            V1SoldItem item = createRandomSoldItem(paymentMethod, purchaseId);
            item.setSoldTime(LocalDateTime.now());
            items.add(item);

        }
        return items;
    }
    private V1SoldItem createRandomSoldItem(V1PaymentMethod paymentMethod,
                                          String purchaseId){
        int seller = Math.abs(random.nextInt() % 9) + 1;
        int price = Math.abs(random.nextInt() % 20) +1 ;

        return new V1SoldItem(purchaseId,
                UUID.randomUUID().toString(),
                LocalDateTime.now(),
                seller,
                price,
                null,
                paymentMethod,
                false);
    }

}
