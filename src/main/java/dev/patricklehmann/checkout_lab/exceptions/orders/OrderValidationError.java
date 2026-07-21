package dev.patricklehmann.checkout_lab.exceptions.orders;

/** A single validation problem with an order line, reported as part of a batch. */
public record OrderValidationError(String sku, OrderValidationReason reason) {}
