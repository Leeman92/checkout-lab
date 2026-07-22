package dev.patricklehmann.checkout_lab.exceptions.orders;

import lombok.Getter;

/**
 * Raised when an operation is rejected because the order is already successfully paid — starting a
 * new payment (FR-017) or cancelling it (FR-028). Mapped to HTTP 409; nothing is changed.
 */
@Getter
public class OrderAlreadyPaidException extends RuntimeException {
    private final long orderId;

    public OrderAlreadyPaidException(long orderId) {
        super("Order '%d' is already paid".formatted(orderId));
        this.orderId = orderId;
    }
}
