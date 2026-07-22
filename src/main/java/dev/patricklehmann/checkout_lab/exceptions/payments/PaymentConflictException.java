package dev.patricklehmann.checkout_lab.exceptions.payments;

import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptStatus;
import lombok.Getter;

/**
 * Raised when a payment result contradicts an already-settled outcome — a second, different
 * terminal result for the same attempt (FR-022), or a success arriving after the order was
 * cancelled (FR-029). The existing outcome is never overwritten; the conflict is recorded (the
 * attempt is flagged and the message is kept in the append-only log) and surfaced as HTTP 409.
 */
@Getter
public class PaymentConflictException extends RuntimeException {
    private final String gatewayReference;
    private final PaymentAttemptStatus existingStatus;
    private final PaymentAttemptStatus attemptedResult;

    public PaymentConflictException(
            String gatewayReference,
            PaymentAttemptStatus existingStatus,
            PaymentAttemptStatus attemptedResult) {
        super(
                "Conflicting result for attempt '%s': already %s, received %s"
                        .formatted(gatewayReference, existingStatus, attemptedResult));
        this.gatewayReference = gatewayReference;
        this.existingStatus = existingStatus;
        this.attemptedResult = attemptedResult;
    }
}
