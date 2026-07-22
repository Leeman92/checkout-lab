package dev.patricklehmann.checkout_lab.entities.payments;

import dev.patricklehmann.checkout_lab.entities.EntityRepository;
import dev.patricklehmann.checkout_lab.entities.orders.Order;
import java.util.List;
import java.util.Optional;

public interface PaymentAttemptRepository extends EntityRepository<PaymentAttempt, Long> {

    /** Finds the attempt a later result message refers to, by its gateway reference (FR-020). */
    Optional<PaymentAttempt> findByGatewayReference(String gatewayReference);

    /**
     * All attempts for an order, oldest first; used to surface payment results on the order
     * (FR-013).
     */
    List<PaymentAttempt> findByOrderOrderByAttemptNumberAsc(Order order);

    /** The most recent attempt for an order; drives attempt numbering and retries (FR-023). */
    Optional<PaymentAttempt> findTopByOrderOrderByAttemptNumberDesc(Order order);

    /** Whether the order has an attempt in the given state; used by the start-payment guards. */
    boolean existsByOrderAndStatus(Order order, PaymentAttemptStatus status);
}
