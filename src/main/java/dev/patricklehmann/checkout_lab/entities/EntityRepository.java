package dev.patricklehmann.checkout_lab.entities;

import java.util.Optional;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;

/**
 * Deliberately curated base repository — a narrower surface than Spring Data's {@code
 * CrudRepository}. Only the operations this project actually uses are exposed, which keeps
 * hand-written test fakes small: a fake only has to implement this bounded set of methods rather
 * than the full CRUD contract. {@code @NoRepositoryBean} means Spring Data never instantiates this
 * interface directly; concrete repositories extend it.
 */
@NoRepositoryBean
public interface EntityRepository<T, ID> extends Repository<T, ID> {
    <S extends T> S save(S entity);

    <S extends T> Iterable<S> saveAll(Iterable<S> entities);

    Optional<T> findById(ID id);

    boolean existsById(ID id);

    Iterable<T> findAll();

    Iterable<T> findAllById(Iterable<ID> ids);

    long count();

    void deleteById(ID id);

    void delete(T entity);

    void deleteAllById(Iterable<? extends ID> ids);

    void deleteAll(Iterable<? extends T> entities);

    void deleteAll();
}
