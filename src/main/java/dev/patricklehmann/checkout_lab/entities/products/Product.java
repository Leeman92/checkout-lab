package dev.patricklehmann.checkout_lab.entities.products;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Entity
// @ToDo: When updating UniqueConstraints also update ProductService::translateIntegrityViolation to
// @ToDo: react to correct constraint Issues
@Table(name = "products", uniqueConstraints = @UniqueConstraint(columnNames = {"sku"}))
@Getter
@Setter
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    private String sku;
    private String name;

    private long netPriceInCents;

    private boolean active;

    private int totalStock;
    private int reservedStock;

    @Transient private double netFormattedPrice;

    @Transient private int availableStock;

    @PostLoad
    private void postLoad() {
        this.netFormattedPrice = netPriceInCents / 100D;
        this.availableStock = totalStock - reservedStock;
    }
}
