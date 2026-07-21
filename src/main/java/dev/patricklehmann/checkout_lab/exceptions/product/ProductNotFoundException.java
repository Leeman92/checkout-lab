package dev.patricklehmann.checkout_lab.exceptions.product;

import lombok.Getter;

/** Raised when a product lookup by SKU finds no matching product; mapped to HTTP 404. */
@Getter
public class ProductNotFoundException extends RuntimeException {
    private final String sku;

    public ProductNotFoundException(String sku) {
        super("Could not find product with given SKU '%s'".formatted(sku));
        this.sku = sku;
    }
}
