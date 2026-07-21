package dev.patricklehmann.checkout_lab.exceptions.product;

/**
 * Domain-level signal that a product with the supplied SKU already exists; mapped to HTTP 409. It
 * is the translated form of the database {@code uniqueSku} constraint violation, deliberately
 * carrying no DB detail so persistence internals never leak to the client.
 */
public class ProductAlreadyExistsException extends RuntimeException {
    public ProductAlreadyExistsException() {
        super("A product with the supplied SKU already exists.");
    }
}
