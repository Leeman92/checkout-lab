package dev.patricklehmann.checkout_lab.entities.payments;

import dev.patricklehmann.checkout_lab.entities.orders.Order;
import dev.patricklehmann.checkout_lab.entities.shared.Money;
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
 * One payment attempt against an {@link Order}. An order may have several attempts over time (e.g.
 * a decline followed by a retry); {@code attemptNumber} orders them and {@code gatewayReference} is
 * the unique key a later callback uses to find the attempt.
 *
 * <p>The attempt holds the <em>effective</em> payment state; every inbound result message is also
 * appended to the {@link PaymentResult} log for dedup and audit.
 */
@Entity
@Table(
        name = "payment_attempts",
        uniqueConstraints = {
            @UniqueConstraint(columnNames = {"gateway_reference"}),
            @UniqueConstraint(columnNames = {"order_id", "attempt_number"})
        })
@Getter
@Setter
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Setter(AccessLevel.PRIVATE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** 1-based sequence per order; distinguishes retries after a decline (FR-023). */
    @Column(nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaymentAttemptStatus status;

    /** Gateway-assigned reference; unique, and the correlation key for later result messages. */
    @Column(nullable = false)
    private String gatewayReference;

    /** The amount charged, snapshotted from the order total at attempt time. */
    @Column(nullable = false)
    private Money amountInCents;

    @Column(nullable = false)
    private Instant createdAt;

    /** When the attempt reached a terminal state; {@code null} while still pending. */
    private Instant resolvedAt;
}
