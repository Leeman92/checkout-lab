package dev.patricklehmann.checkout_lab.exceptions.orders;

/** A single order line that could not be fully reserved, reported as part of a batch. */
public record StockShortfall(String sku, int requested, int available) {}
