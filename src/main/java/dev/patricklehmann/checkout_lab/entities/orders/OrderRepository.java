package dev.patricklehmann.checkout_lab.entities.orders;

import dev.patricklehmann.checkout_lab.entities.EntityRepository;
import java.util.Optional;

public interface OrderRepository extends EntityRepository<Order, Long> {

    /**
     * Looks up an order by its idempotency key to detect replays and key conflicts (FR-011/012).
     */
    Optional<Order> findByIdempotencyKey(String idempotencyKey);
}
