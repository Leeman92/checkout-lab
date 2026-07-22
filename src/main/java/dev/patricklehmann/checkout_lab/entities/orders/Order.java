package dev.patricklehmann.checkout_lab.entities.orders;

import dev.patricklehmann.checkout_lab.entities.shared.Money;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * An order aggregate: one or more {@link OrderItem} positions, a business {@link OrderStatus}, and
 * the monetary total captured at order time.
 *
 * <p>Named {@code orders} at the table level because {@code order} is a reserved SQL word.
 */
@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(columnNames = {"idempotency_key"}))
@Getter
@Setter
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Setter(AccessLevel.PRIVATE)
    private Long id;

    /**
     * Optimistic-locking version. Hibernate increments it on each update and rejects a write whose
     * version is stale, so two concurrent transitions of the same order (e.g. pay vs. cancel)
     * cannot both commit (FR-024 / FR-029). Managed by the persistence provider — never set by
     * hand.
     */
    @Version
    @Setter(AccessLevel.NONE)
    private long version;

    /** Client-supplied retry key; the unique constraint makes it the idempotency guarantee. */
    @Column(nullable = false)
    private String idempotencyKey;

    /** SHA-256 hex of the normalized request payload; used to detect idempotency-key conflicts. */
    @Column(nullable = false, length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    /** Order total (EUR). Exact: sum of line values, no rounding. */
    @Column(nullable = false)
    private Money totalNetInCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Instant createdAt;

    /**
     * Last time the order changed (status transition). Set via the injected clock,
     * not @UpdateTimestamp, so it stays deterministic in tests.
     */
    @Column(nullable = false)
    private Instant updatedAt;

    // Managed by hand rather than via Lombok so we never hand out the mutable backing list
    // (SpotBugs EI_EXPOSE_REP). Hibernate uses field access, so no public setter is required.
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @OneToMany(
            mappedBy = "order",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    /** Returns an unmodifiable view of the order's line items. */
    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(this.items);
    }

    /** Adds a line item and keeps both sides of the bidirectional association in sync. */
    public void addItem(OrderItem item) {
        item.setOrder(this);
        this.items.add(item);
    }
}
