package dev.patricklehmann.checkout_lab.controller.api.payments.dto;

import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptStatus;

/**
 * Body for starting a payment. {@code desiredOutcome} is the simulation hint (nullable → the
 * gateway defaults to SUCCESS); set it to PENDING to exercise the delayed-callback path.
 */
public record StartPaymentRequest(PaymentAttemptStatus desiredOutcome) {}
