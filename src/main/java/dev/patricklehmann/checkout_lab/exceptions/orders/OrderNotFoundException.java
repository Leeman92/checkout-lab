package dev.patricklehmann.checkout_lab.exceptions.orders;

import lombok.Getter;

/** Raised when an order lookup by id finds no matching order; mapped to HTTP 404. */
@Getter
public class OrderNotFoundException extends RuntimeException {
    private final long orderId;

    public OrderNotFoundException(long orderId) {
        super("Could not find order with id '%d'".formatted(orderId));
        this.orderId = orderId;
    }
}
