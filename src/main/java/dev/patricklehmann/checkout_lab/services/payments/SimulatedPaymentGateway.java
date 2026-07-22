package dev.patricklehmann.checkout_lab.services.payments;

import dev.patricklehmann.checkout_lab.entities.orders.Order;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * The application's payment gateway. There is no real provider in this project, so the outcome is
 * simulated: it returns the caller's {@code desiredOutcome} (defaulting to {@link
 * PaymentAttemptStatus#SUCCESS}) and mints a unique gateway reference.
 *
 * <p>Letting the request choose the outcome is a deliberate <em>simulation affordance</em> — it
 * lets every path (success, decline, and delayed pending) be exercised through the API — not
 * something a real gateway would allow.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    @Override
    public PaymentAuthorization authorize(Order order, PaymentAttemptStatus desiredOutcome) {
        PaymentAttemptStatus outcome =
                desiredOutcome != null ? desiredOutcome : PaymentAttemptStatus.SUCCESS;
        String gatewayReference = "sim-" + order.getId() + "-" + UUID.randomUUID();
        return new PaymentAuthorization(outcome, gatewayReference);
    }
}
