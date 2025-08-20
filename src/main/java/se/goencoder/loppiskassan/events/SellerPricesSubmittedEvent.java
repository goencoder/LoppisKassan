package se.goencoder.loppiskassan.events;

/**
 * Event emitted when the user submits seller and price information.
 * Carries the raw text from the input fields so that controllers
 * can validate and process the data.
 */
public record SellerPricesSubmittedEvent(String sellerText, String pricesText) {
}

