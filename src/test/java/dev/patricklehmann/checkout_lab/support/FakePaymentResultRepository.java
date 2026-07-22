package dev.patricklehmann.checkout_lab.support;

import dev.patricklehmann.checkout_lab.entities.payments.PaymentResult;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentResultRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link PaymentResultRepository} for fast unit tests. The append-only log's uniqueness
 * on {@code messageId} — the dedup key (FR-021) — is enforced here by {@link #existsByMessageId}.
 */
public final class FakePaymentResultRepository implements PaymentResultRepository {

    public final List<PaymentResult> saved = new ArrayList<>();
    public final Map<Long, PaymentResult> byId = new LinkedHashMap<>();

    @Override
    public boolean existsByMessageId(String messageId) {
        return saved.stream().anyMatch(result -> messageId.equals(result.getMessageId()));
    }

    @Override
    public <S extends PaymentResult> S save(S entity) {
        saved.add(entity);
        return entity;
    }

    @Override
    public <S extends PaymentResult> Iterable<S> saveAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        entities.forEach(
                entity -> {
                    saved.add(entity);
                    result.add(entity);
                });
        return result;
    }

    @Override
    public Optional<PaymentResult> findById(Long id) {
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public boolean existsById(Long id) {
        return byId.containsKey(id);
    }

    @Override
    public Iterable<PaymentResult> findAll() {
        return saved;
    }

    @Override
    public Iterable<PaymentResult> findAllById(Iterable<Long> ids) {
        List<PaymentResult> matches = new ArrayList<>();
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
    public void delete(PaymentResult entity) {
        saved.remove(entity);
    }

    @Override
    public void deleteAllById(Iterable<? extends Long> ids) {
        ids.forEach(this::deleteById);
    }

    @Override
    public void deleteAll(Iterable<? extends PaymentResult> entities) {
        entities.forEach(this::delete);
    }

    @Override
    public void deleteAll() {
        saved.clear();
        byId.clear();
    }
}
