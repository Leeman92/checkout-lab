package dev.patricklehmann.checkout_lab.entities.products;

import dev.patricklehmann.checkout_lab.entities.EntityRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends EntityRepository<Product, Long> {
    Optional<Product> findBySku(String sku);
    List<Product> findAllBySkuIn(Collection<String> skus);
}
