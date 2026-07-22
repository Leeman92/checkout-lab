package dev.patricklehmann.checkout_lab.support;

import dev.patricklehmann.checkout_lab.entities.products.Product;
import dev.patricklehmann.checkout_lab.entities.products.ProductRepository;
import dev.patricklehmann.checkout_lab.entities.shared.Sku;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link ProductRepository} for fast unit tests. The atomic {@code tryReserveStock} /
 * {@code releaseStock} conditional updates are emulated in plain Java (single-threaded), preserving
 * the row-count contract the service relies on.
 */
public final class FakeProductRepository implements ProductRepository {

    public final Map<Sku, Product> bySku = new LinkedHashMap<>();
    public final Map<Long, Product> byId = new LinkedHashMap<>();

    public void add(Product product) {
        bySku.put(product.getSku(), product);
        byId.put(product.getId(), product);
    }

    @Override
    public Optional<Product> findBySku(Sku sku) {
        return Optional.ofNullable(bySku.get(sku));
    }

    @Override
    public List<Product> findAllBySkuIn(Collection<Sku> skus) {
        return skus.stream().map(bySku::get).filter(product -> product != null).toList();
    }

    @Override
    public int tryReserveStock(Long productId, int quantity) {
        Product product = byId.get(productId);
        if (product != null && product.getAvailableStock() >= quantity) {
            product.setReservedStock(product.getReservedStock() + quantity);
            return 1;
        }
        return 0;
    }

    @Override
    public int releaseStock(Long productId, int quantity) {
        Product product = byId.get(productId);
        if (product != null && product.getReservedStock() >= quantity) {
            product.setReservedStock(product.getReservedStock() - quantity);
            return 1;
        }
        return 0;
    }

    @Override
    public <S extends Product> S save(S entity) {
        add(entity);
        return entity;
    }

    @Override
    public <S extends Product> Iterable<S> saveAll(Iterable<S> entities) {
        List<S> saved = new ArrayList<>();
        entities.forEach(
                entity -> {
                    add(entity);
                    saved.add(entity);
                });
        return saved;
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public boolean existsById(Long id) {
        return byId.containsKey(id);
    }

    @Override
    public Iterable<Product> findAll() {
        return bySku.values();
    }

    @Override
    public Iterable<Product> findAllById(Iterable<Long> ids) {
        List<Product> matches = new ArrayList<>();
        ids.forEach(id -> findById(id).ifPresent(matches::add));
        return matches;
    }

    @Override
    public long count() {
        return bySku.size();
    }

    @Override
    public void deleteById(Long id) {
        findById(id).ifPresent(this::delete);
    }

    @Override
    public void delete(Product entity) {
        bySku.remove(entity.getSku());
        byId.remove(entity.getId());
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
        ids.forEach(this::deleteById);
    }

    @Override
    public void deleteAll(Iterable<? extends Product> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public void deleteAll() {
        bySku.clear();
        byId.clear();
    }
}
