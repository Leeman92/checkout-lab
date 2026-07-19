package dev.patricklehmann.checkout_lab.exceptions.product;

import lombok.Getter;

@Getter
public class ProductNotFoundException extends RuntimeException {
    private final String sku;

    public ProductNotFoundException(String sku) {
        super("Could not find product with given SKU '%s'".formatted(sku));
        this.sku = sku;
    }
}
