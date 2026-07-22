package dev.patricklehmann.checkout_lab.controller.api.orders.dto;

import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttempt;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptStatus;
import java.time.Instant;

/**
 * A payment attempt as seen from the order query (FR-013) — the "known payment results" for an
 * order. Deliberately lighter than the payments API's {@code PaymentAttemptResponse}: no gateway
 * reference or echoed order status, just what a client needs to see an order's payment history.
 */
public record OrderPaymentView(
        int attemptNumber,
        PaymentAttemptStatus status,
        long amountInCents,
        Instant createdAt,
        Instant resolvedAt) {

    public static OrderPaymentView from(PaymentAttempt attempt) {
        return new OrderPaymentView(
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getAmountInCents().amountInCents(),
                attempt.getCreatedAt(),
                attempt.getResolvedAt());
    }
}
