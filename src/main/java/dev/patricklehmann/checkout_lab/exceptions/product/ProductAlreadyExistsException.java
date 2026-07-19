package dev.patricklehmann.checkout_lab.exceptions.product;

public class ProductAlreadyExistsException extends RuntimeException {
    public ProductAlreadyExistsException() {
        super("A product with the supplied SKU already exists.");
    }
}
