package dev.patricklehmann.checkout_lab.exceptions.product;

public class ProductAlreadyExistsException extends RuntimeException {
    public ProductAlreadyExistsException(String message) {
        super(message);
    }
}
