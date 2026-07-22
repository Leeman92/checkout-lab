package dev.patricklehmann.checkout_lab.support;

import dev.patricklehmann.checkout_lab.entities.orders.Order;
import dev.patricklehmann.checkout_lab.entities.orders.OrderRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link OrderRepository} for fast unit tests (no Spring, no database). */
public final class FakeOrderRepository implements OrderRepository {

    public final List<Order> saved = new ArrayList<>();
    public final Map<Long, Order> byId = new LinkedHashMap<>();

    @Override
    public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
        return saved.stream()
                .filter(order -> idempotencyKey.equals(order.getIdempotencyKey()))
                .findFirst();
    }

    @Override
    public <S extends Order> S save(S entity) {
        saved.add(entity);
        return entity;
    }

    @Override
    public <S extends Order> Iterable<S> saveAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        entities.forEach(
                entity -> {
                    saved.add(entity);
                    result.add(entity);
                });
        return result;
    }

    @Override
    public Optional<Order> findById(Long id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public boolean existsById(Long id) {
        return byId.containsKey(id);
    }

    @Override
    public Iterable<Order> findAll() {
        return saved;
    }

    @Override
    public Iterable<Order> findAllById(Iterable<Long> ids) {
        List<Order> matches = new ArrayList<>();
        ids.forEach(id -> findById(id).ifPresent(matches::add));
        return matches;
    }

    @Override
    public long count() {
        return saved.size();
    }

    @Override
    public void deleteById(Long id) {
        byId.remove(id);
    }

    @Override
    public void delete(Order entity) {
        saved.remove(entity);
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
        ids.forEach(this::deleteById);
    }

    @Override
    public void deleteAll(Iterable<? extends Order> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public void deleteAll() {
        saved.clear();
        byId.clear();
    }
}
