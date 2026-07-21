package dev.patricklehmann.checkout_lab.entities.products;

import dev.patricklehmann.checkout_lab.entities.shared.Money;
import dev.patricklehmann.checkout_lab.entities.shared.Sku;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

/**
 * A sellable product: its {@link Sku}, display name, net price, active flag, and stock counters.
 *
 * <p>Stock is tracked as {@code totalStock} minus {@code reservedStock}; the named {@code
 * uniqueSku} constraint enforces one product per SKU and is the constraint {@code ProductService}
 * translates into a domain {@code ProductAlreadyExistsException}.
 */
@Entity
@Table(
        name = "products",
        uniqueConstraints =
                @UniqueConstraint(
                        columnNames = {"sku"},
                        name = "uniqueSku"))
@Getter
@Setter
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Setter(AccessLevel.PRIVATE)
    private Long id;

    private Sku sku;
    private String name;

    private Money netPriceInCents;

    private boolean active;

    private int totalStock;
    private int reservedStock;

    /** Convenience view of the net price as a plain EUR decimal string (e.g. {@code "19.99"}). */
    public String getNetFormattedPrice() {
        return this.netPriceInCents.formatted();
    }

    /** Stock available to reserve: total on hand minus what is already reserved. */
    public int getAvailableStock() {
        return this.totalStock - reservedStock;
    }
}
