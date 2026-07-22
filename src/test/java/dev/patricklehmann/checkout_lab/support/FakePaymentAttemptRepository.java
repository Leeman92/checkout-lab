package dev.patricklehmann.checkout_lab.support;

import dev.patricklehmann.checkout_lab.entities.orders.Order;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttempt;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptRepository;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptStatus;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory {@link PaymentAttemptRepository} for fast unit tests. */
public final class FakePaymentAttemptRepository implements PaymentAttemptRepository {

    public final List<PaymentAttempt> saved = new ArrayList<>();
    public final Map<Long, PaymentAttempt> byId = new LinkedHashMap<>();

    @Override
    public Optional<PaymentAttempt> findByGatewayReference(String gatewayReference) {
        return saved.stream()
                .filter(attempt -> gatewayReference.equals(attempt.getGatewayReference()))
                .findFirst();
    }

    @Override
    public List<PaymentAttempt> findByOrderOrderByAttemptNumberAsc(Order order) {
        return saved.stream()
                .filter(attempt -> attempt.getOrder() == order)
                .sorted(Comparator.comparingInt(PaymentAttempt::getAttemptNumber))
                .toList();
    }

    @Override
    public Optional<PaymentAttempt> findTopByOrderOrderByAttemptNumberDesc(Order order) {
        return saved.stream()
                .filter(attempt -> attempt.getOrder() == order)
                .max(Comparator.comparingInt(PaymentAttempt::getAttemptNumber));
    }

    @Override
    public boolean existsByOrderAndStatus(Order order, PaymentAttemptStatus status) {
        return saved.stream()
                .anyMatch(attempt -> attempt.getOrder() == order && attempt.getStatus() == status);
    }

    @Override
    public <S extends PaymentAttempt> S save(S entity) {
        saved.add(entity);
        return entity;
    }

    @Override
    public <S extends PaymentAttempt> Iterable<S> saveAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        entities.forEach(
                entity -> {
                    saved.add(entity);
                    result.add(entity);
                });
        return result;
    }

    @Override
    public Optional<PaymentAttempt> findById(Long id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public boolean existsById(Long id) {
        return byId.containsKey(id);
    }

    @Override
    public Iterable<PaymentAttempt> findAll() {
        return saved;
    }

    @Override
    public Iterable<PaymentAttempt> findAllById(Iterable<Long> ids) {
        List<PaymentAttempt> matches = new ArrayList<>();
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
    public void delete(PaymentAttempt entity) {
        saved.remove(entity);
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
        ids.forEach(this::deleteById);
    }

    @Override
    public void deleteAll(Iterable<? extends PaymentAttempt> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public void deleteAll() {
        saved.clear();
        byId.clear();
    }
}
