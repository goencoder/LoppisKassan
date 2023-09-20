package se.teddy.loppiskassan.records;

/**
 * Enum som representerar de tillgängliga betalningstyperna för sålda objekt.
 *
 * @author gengdahl
 * @since 2016-08-18
 */
public enum PaymentType {
    /** Kontant betalning. */
    Cash,
    /** Betalning via Swish. */
    Swish;
}
