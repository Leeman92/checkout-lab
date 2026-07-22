package dev.patricklehmann.checkout_lab.entities.products;

import dev.patricklehmann.checkout_lab.entities.EntityRepository;
import dev.patricklehmann.checkout_lab.entities.shared.Sku;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence access for {@link Product}, including the atomic stock-reservation operation. */
public interface ProductRepository extends EntityRepository<Product, Long> {
    Optional<Product> findBySku(Sku sku);

    List<Product> findAllBySkuIn(Collection<Sku> skus);

    /**
     * Atomically reserves {@code quantity} units for the given product, in a single conditional
     * {@code UPDATE} that only succeeds while enough stock remains available. Performing the check
     * and the increment as one statement (rather than read-then-write in Java) is the sole
     * mechanism preventing overselling under concurrency (FR-009 / FR-010 / AC-009).
     *
     * @return {@code 1} if the reservation was applied, {@code 0} if stock was insufficient
     */
    @Modifying
    @Query(
            """
        UPDATE
            Product p
        SET
            p.reservedStock = p.reservedStock + :quantity
        WHERE
            p.id = :productId AND
            (p.totalStock - p.reservedStock >= :quantity)
        """)
    int tryReserveStock(@Param("productId") Long productId, @Param("quantity") int quantity);

    /**
     * Atomically releases {@code quantity} previously-reserved units for the given product, used
     * when an order is cancelled (FR-026). The mirror of {@link #tryReserveStock}: a single
     * conditional {@code UPDATE} that decrements {@code reservedStock} only while at least that
     * many units are actually reserved, so the counter can never be driven negative.
     *
     * @return {@code 1} if the release was applied, {@code 0} if there was not that much reserved
     */
    @Modifying
    @Query(
            """
        UPDATE
            Product p
        SET
            p.reservedStock = p.reservedStock - :quantity
        WHERE
            p.id = :productId AND
            p.reservedStock >= :quantity
        """)
    int releaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);
}
