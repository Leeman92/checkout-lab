package dev.patricklehmann.checkout_lab.entities.payments;

import dev.patricklehmann.checkout_lab.entities.EntityRepository;

public interface PaymentResultRepository extends EntityRepository<PaymentResult, Long> {

    /** Whether a result message with this id was already recorded; the dedup check (FR-021). */
    boolean existsByMessageId(String messageId);
}
