package dev.patricklehmann.checkout_lab.controller.api.payments.dto;

import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Body of a delayed/duplicate payment result message (FR-020/021/022).
 *
 * <p>{@code gatewayReference} identifies the attempt; {@code messageId} is the idempotency key that
 * makes a redelivery a no-op; {@code result} must be a terminal outcome (a callback never delivers
 * PENDING).
 */
public record PaymentCallbackRequest(
        @NotBlank String gatewayReference,
        @NotNull PaymentAttemptStatus result,
        @NotBlank String messageId) {

    /** A callback carries a settled outcome; PENDING is not a valid result to report. */
    @AssertTrue(message = "result must be a terminal outcome (SUCCESS or DECLINED)")
    public boolean isTerminalResult() {
        return result == null || result != PaymentAttemptStatus.PENDING;
    }
}
