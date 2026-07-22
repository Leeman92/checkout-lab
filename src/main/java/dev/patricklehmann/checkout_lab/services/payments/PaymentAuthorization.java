package dev.patricklehmann.checkout_lab.services.payments;

import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptStatus;

/**
 * The result of a gateway authorization: the outcome and the unique reference the gateway assigns,
 * which later result messages use to identify this attempt.
 */
public record PaymentAuthorization(PaymentAttemptStatus status, String gatewayReference) {}
