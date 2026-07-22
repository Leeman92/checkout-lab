package dev.patricklehmann.checkout_lab.exceptions.payments;

import lombok.Getter;

/**
 * Raised when an inbound result message references a gateway reference that matches no known
 * attempt (FR-020). Mapped to HTTP 404.
 */
@Getter
public class PaymentAttemptNotFoundException extends RuntimeException {
    private final String gatewayReference;

    public PaymentAttemptNotFoundException(String gatewayReference) {
        super("No payment attempt found for gateway reference '%s'".formatted(gatewayReference));
        this.gatewayReference = gatewayReference;
    }
}
