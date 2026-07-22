package dev.patricklehmann.checkout_lab.services.payments;

import dev.patricklehmann.checkout_lab.entities.orders.Order;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptStatus;

/**
 * Port to a payment provider. The application uses a simulated implementation; tests substitute a
 * scripted one. Keeping this an interface (rather than calling a provider SDK directly) is the seam
 * that makes payment outcomes controllable and deterministic in tests (NFR-008).
 */
public interface PaymentGateway {

    /**
     * Authorizes a payment for the given order and reports the outcome.
     *
     * @param order the order being paid (its total is the amount)
     * @param desiredOutcome simulation hint selecting the outcome; {@code null} means default
     * @return the outcome plus a unique gateway reference identifying this authorization
     */
    PaymentAuthorization authorize(Order order, PaymentAttemptStatus desiredOutcome);
}
