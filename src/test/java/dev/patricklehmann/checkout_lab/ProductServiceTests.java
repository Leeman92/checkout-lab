package dev.patricklehmann.checkout_lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patricklehmann.checkout_lab.entities.products.Product;
import dev.patricklehmann.checkout_lab.entities.products.ProductRepository;
import dev.patricklehmann.checkout_lab.exceptions.product.ProductAlreadyExistsException;
import dev.patricklehmann.checkout_lab.exceptions.product.ProductNotFoundException;
import dev.patricklehmann.checkout_lab.services.products.ProductService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.springframework.dao.DataIntegrityViolationException;

class ProductServiceTests {

    private final FakeProductRepository repository = new FakeProductRepository();
    private final ProductService service = new ProductService(repository);

    @Test
    void returnsProductBySku() {
        Product product = product("TSHIRT-BLK-M");
        repository.products.put(product.getSku(), product);

        assertThat(service.listProductBySku("TSHIRT-BLK-M")).isSameAs(product);
    }

    @Test
    void throwsSpecificExceptionWhenSkuDoesNotExist() {
        assertThatThrownBy(() -> service.listProductBySku("MISSING-SKU"))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessage("Could not find product with given SKU 'MISSING-SKU'")
                .extracting("sku")
                .isEqualTo("MISSING-SKU");
    }

    @Test
    void savesAllProducts() {
        List<Product> products = List.of(product("TSHIRT-BLK-M"), product("HOODIE-GRY-M"));

        assertThat(service.saveAll(products)).containsExactlyElementsOf(products);
        assertThat(repository.products.values()).containsExactlyElementsOf(products);
    }

    @Test
    void translatesNestedPostgresUniqueViolationIntoConflictException() {
        PSQLException postgresException =
                new PSQLException("duplicate SKU", PSQLState.UNIQUE_VIOLATION);
        repository.saveAllFailure =
                new DataIntegrityViolationException(
                        "could not execute statement",
                        new IllegalStateException(postgresException));

        assertThatThrownBy(() -> service.saveAll(List.of(product("TSHIRT-BLK-M"))))
                .isInstanceOf(ProductAlreadyExistsException.class)
                .hasMessage("A product with the supplied SKU already exists.");
    }

    @Test
    void translatesUniqueViolationWhenSavingOneProduct() {
        repository.saveFailure =
                new DataIntegrityViolationException(
                        "duplicate SKU",
                        new PSQLException("duplicate SKU", PSQLState.UNIQUE_VIOLATION));

        assertThatThrownBy(() -> service.save(product("TSHIRT-BLK-M")))
                .isInstanceOf(ProductAlreadyExistsException.class)
                .hasMessage("A product with the supplied SKU already exists.");
    }

    @Test
    void doesNotHideOtherDatabaseIntegrityFailures() {
        DataIntegrityViolationException failure =
                new DataIntegrityViolationException("not a unique constraint violation");
        repository.saveAllFailure = failure;

        assertThatThrownBy(() -> service.saveAll(List.of(product("TSHIRT-BLK-M"))))
                .isSameAs(failure);
    }

    private static Product product(String sku) {
        Product product = new Product();
        product.setSku(sku);
        return product;
    }

    private static final class FakeProductRepository implements ProductRepository {

        private final Map<String, Product> products = new LinkedHashMap<>();
        private RuntimeException saveFailure;
        private RuntimeException saveAllFailure;

        @Override
        public Optional<Product> findBySku(String sku) {
            return Optional.ofNullable(products.get(sku));
        }

        @Override
        public List<Product> findAllBySkuIn(Collection<String> skus) {
            return skus.stream().map(products::get).filter(product -> product != null).toList();
        }

        @Override
        public <S extends Product> S save(S entity) {
            if (saveFailure != null) {
                throw saveFailure;
            }

            products.put(entity.getSku(), entity);
            return entity;
        }

        @Override
        public <S extends Product> Iterable<S> saveAll(Iterable<S> entities) {
            if (saveAllFailure != null) {
                throw saveAllFailure;
            }

            List<S> saved = new ArrayList<>();
            entities.forEach(
                    product -> {
                        save(product);
                        saved.add(product);
                    });
            return saved;
        }

        @Override
        public Optional<Product> findById(Long id) {
            return products.values().stream()
                    .filter(product -> id.equals(product.getId()))
                    .findFirst();
        }

        @Override
        public boolean existsById(Long id) {
            return findById(id).isPresent();
        }

        @Override
        public Iterable<Product> findAll() {
            return products.values();
        }

        @Override
        public Iterable<Product> findAllById(Iterable<Long> ids) {
            List<Product> matches = new ArrayList<>();
            ids.forEach(id -> findById(id).ifPresent(matches::add));
            return matches;
        }

        @Override
        public long count() {
            return products.size();
        }

        @Override
        public void deleteById(Long id) {
            findById(id).ifPresent(this::delete);
        }

        @Override
        public void delete(Product entity) {
            products.remove(entity.getSku());
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
            products.clear();
        }
    }
}
