package dev.patricklehmann.checkout_lab.support;

import dev.patricklehmann.checkout_lab.entities.orders.Order;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptStatus;
import dev.patricklehmann.checkout_lab.services.payments.PaymentAuthorization;
import dev.patricklehmann.checkout_lab.services.payments.PaymentGateway;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Deterministic {@link PaymentGateway} for unit tests. Gateway references are predictable ({@code
 * gw-1}, {@code gw-2}, ...) so tests can address a later callback without capturing a random id. By
 * default it echoes the requested {@code desiredOutcome} (SUCCESS when none is given); tests that
 * need the gateway to disagree with the request can {@link #enqueue} forced outcomes.
 */
public final class ScriptedPaymentGateway implements PaymentGateway {

    private final Deque<PaymentAttemptStatus> forcedOutcomes = new ArrayDeque<>();
    private int counter;

    /** Forces the next authorization(s) to a given outcome regardless of the requested one. */
    public void enqueue(PaymentAttemptStatus... outcomes) {
        Collections.addAll(forcedOutcomes, outcomes);
    }

    @Override
    public PaymentAuthorization authorize(Order order, PaymentAttemptStatus desiredOutcome) {
        PaymentAttemptStatus outcome =
                forcedOutcomes.isEmpty()
                        ? (desiredOutcome == null ? PaymentAttemptStatus.SUCCESS : desiredOutcome)
                        : forcedOutcomes.poll();
        counter++;
        return new PaymentAuthorization(outcome, "gw-" + counter);
    }

    /** The gateway references handed out so far, in order (e.g. for asserting attempt wiring). */
    public List<String> issuedReferences() {
        List<String> references = new java.util.ArrayList<>();
        for (int i = 1; i <= counter; i++) {
            references.add("gw-" + i);
        }
        return references;
    }
}
