package dev.patricklehmann.checkout_lab.entities.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * An append-only record of one inbound payment result message for an attempt.
 *
 * <p>Rows are never updated or deleted. The unique {@code messageId} makes redelivery of the same
 * message a no-op (FR-021), and the full history keeps conflicting results traceable rather than
 * silently overwritten (FR-022). Only terminal results ({@code SUCCESS} / {@code DECLINED}) are
 * ever logged.
 */
@Entity
@Table(
        name = "payment_results",
        uniqueConstraints = @UniqueConstraint(columnNames = {"message_id"}))
@Getter
@Setter
public class PaymentResult {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Setter(AccessLevel.PRIVATE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private PaymentAttempt attempt;

    /** Idempotency key of the inbound message; unique, so a duplicate delivery is ignored. */
    @Column(nullable = false)
    private String messageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentAttemptStatus result;

    @Column(nullable = false)
    private Instant receivedAt;
}
