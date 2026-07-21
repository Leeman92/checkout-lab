package dev.patricklehmann.checkout_lab.entities.orders;

import dev.patricklehmann.checkout_lab.entities.shared.Money;
import dev.patricklehmann.checkout_lab.entities.shared.Sku;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * A single order position.
 *
 * <p>The {@code sku} and {@code unitNetPriceInCents} are <strong>snapshots</strong> taken at order
 * time, deliberately not a foreign key to {@code products}. This decouples the historical order
 * from the live product, so a later product price change cannot alter this order (FR-006).
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Setter(AccessLevel.PRIVATE)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** SKU snapshot at order time (no FK to products). */
    @Column(nullable = false)
    private Sku sku;

    @Column(nullable = false)
    private int quantity;

    /** Unit price snapshot at order time (FR-006). */
    @Column(nullable = false)
    private Money unitNetPriceInCents;

    /**
     * Persisted line value = {@code unitNetPriceInCents * quantity}; auditable against the total.
     */
    @Column(nullable = false)
    private Money lineNetInCents;
}
