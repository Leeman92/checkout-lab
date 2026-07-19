package dev.patricklehmann.checkout_lab.services.products;

import dev.patricklehmann.checkout_lab.entities.products.Product;
import dev.patricklehmann.checkout_lab.entities.products.ProductRepository;
import dev.patricklehmann.checkout_lab.exceptions.product.ProductAlreadyExistsException;
import dev.patricklehmann.checkout_lab.exceptions.product.ProductNotFoundException;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PSQLException;
import org.postgresql.util.PSQLState;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public Iterable<Product> listProducts() {
        return this.productRepository.findAll();
    }

    public Product listProductBySku(String sku) {
        return this.productRepository
                .findBySku(sku)
                .orElseThrow(() -> new ProductNotFoundException(sku));
    }

    public Iterable<Product> listProductBySku(Collection<String> skus) {
        return this.productRepository.findAllBySkuIn(skus);
    }

    public Product save(Product product) {
        try {
            return this.productRepository.save(product);
        } catch (DataIntegrityViolationException exception) {
            throw translateIntegrityViolation(exception);
        }
    }

    public Iterable<Product> saveAll(List<Product> products) {
        try {
            return this.productRepository.saveAll(products);
        } catch (DataIntegrityViolationException exception) {
            throw translateIntegrityViolation(exception);
        }
    }

    private static RuntimeException translateIntegrityViolation(
            DataIntegrityViolationException exception) {
        PSQLException postgresException = findCause(exception, PSQLException.class);

        if (postgresException != null
                && PSQLState.UNIQUE_VIOLATION.getState().equals(postgresException.getSQLState())) {

            ServerErrorMessage serverError = postgresException.getServerErrorMessage();
            String detail = serverError != null ? serverError.getDetail() : null;

            return new ProductAlreadyExistsException(
                    detail != null ? detail : "A product with the supplied SKU already exists.");
        }

        return exception;
    }

    private static <T extends Throwable> T findCause(Throwable exception, Class<T> expectedType) {
        Throwable current = exception;

        while (current != null) {
            if (expectedType.isInstance(current)) {
                return expectedType.cast(current);
            }

            current = current.getCause();
        }

        return null;
    }
}
