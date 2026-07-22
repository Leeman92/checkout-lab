package dev.patricklehmann.checkout_lab.exceptions.payments;

import lombok.Getter;

/**
 * Raised when a new payment is started while a PENDING attempt for the same order is still
 * unresolved. Prevents two concurrent in-flight attempts on one order (FR-024). Mapped to HTTP 409.
 */
@Getter
public class PaymentInProgressException extends RuntimeException {
    private final long orderId;

    public PaymentInProgressException(long orderId) {
        super("Order '%d' already has a payment attempt in progress".formatted(orderId));
        this.orderId = orderId;
    }
}
