package dev.patricklehmann.checkout_lab.controller.api.payments.dto;

import dev.patricklehmann.checkout_lab.entities.orders.OrderStatus;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttempt;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptStatus;
import java.time.Instant;

/**
 * API response view of a payment attempt, including the owning order's current status so a client
 * sees both the attempt outcome and its effect. {@code amountInCents} is bare cents, matching the
 * money wire contract.
 */
public record PaymentAttemptResponse(
        Long id,
        Long orderId,
        int attemptNumber,
        PaymentAttemptStatus status,
        String gatewayReference,
        long amountInCents,
        Instant createdAt,
        Instant resolvedAt,
        OrderStatus orderStatus) {

    /**
     * Projects a persisted {@link PaymentAttempt} (and its order's status) onto its response view.
     */
    public static PaymentAttemptResponse from(PaymentAttempt attempt) {
        return new PaymentAttemptResponse(
                attempt.getId(),
                attempt.getOrder().getId(),
                attempt.getAttemptNumber(),
                attempt.getStatus(),
                attempt.getGatewayReference(),
                attempt.getAmountInCents().amountInCents(),
                attempt.getCreatedAt(),
                attempt.getResolvedAt(),
                attempt.getOrder().getStatus());
    }
}
