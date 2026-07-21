package dev.patricklehmann.checkout_lab.services.products;

import dev.patricklehmann.checkout_lab.entities.products.Product;
import dev.patricklehmann.checkout_lab.entities.products.ProductRepository;
import dev.patricklehmann.checkout_lab.entities.shared.Sku;
import dev.patricklehmann.checkout_lab.exceptions.product.ProductAlreadyExistsException;
import dev.patricklehmann.checkout_lab.exceptions.product.ProductNotFoundException;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Application service for products: read access plus persistence that turns database uniqueness
 * failures into meaningful domain errors.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public Iterable<Product> listProducts() {
        return this.productRepository.findAll();
    }

    /**
     * Looks up a single product by SKU.
     *
     * @throws ProductNotFoundException if no product with that SKU exists
     */
    public Product listProductBySku(Sku sku) {
        return this.productRepository
                .findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku.value()));
    }

    public Iterable<Product> listProductBySku(Collection<Sku> skus) {
        return this.productRepository.findAllBySkuIn(skus);
    }

    /**
     * Persists a product, translating a unique-SKU violation into a domain {@link
     * ProductAlreadyExistsException} so callers never see raw persistence exceptions.
     */
    public Product save(Product product) {
        try {
            return this.productRepository.save(product);
        } catch (DataIntegrityViolationException exception) {
            throw translateIntegrityViolation(exception);
        }
    }

    /** Batch counterpart to {@link #save}; the whole batch shares one transaction. */
    public Iterable<Product> saveAll(List<Product> products) {
        try {
            return this.productRepository.saveAll(products);
        } catch (DataIntegrityViolationException exception) {
            throw translateIntegrityViolation(exception);
        }
    }

    /**
     * Maps a Spring {@link DataIntegrityViolationException} to a domain exception. When the root
     * cause is a Hibernate {@code ConstraintViolationException} (the {@code uniqueSku} constraint),
     * it returns {@link ProductAlreadyExistsException} without exposing the underlying DB detail.
     * Any other integrity violation is genuinely unexpected, so it is logged and rethrown as-is.
     */
    private static RuntimeException translateIntegrityViolation(
            DataIntegrityViolationException exception) {

        ConstraintViolationException constraintViolation =
                findCause(exception, ConstraintViolationException.class);

        if (constraintViolation == null) {
            log.error("Unhandled data integrity violation", exception);
            throw exception;
        }

        log.info(
                "Product uniqueness constraint violated: {}",
                constraintViolation.getConstraintName());

        return new ProductAlreadyExistsException();
    }

    /**
     * Walks the exception cause chain and returns the first cause assignable to {@code
     * expectedType}, or {@code null} if none is found. Needed because the meaningful Hibernate
     * cause is nested several levels below the Spring wrapper.
     */
    private static <T extends Throwable> T findCause(Throwable throwable, Class<T> expectedType) {
        Throwable current = throwable;

        while (current != null) {
            if (expectedType.isInstance(current)) {
                return expectedType.cast(current);
            }

            current = current.getCause();
        }

        return null;
    }
}
