package dev.patricklehmann.checkout_lab.services.products;

import dev.patricklehmann.checkout_lab.entities.products.Product;
import dev.patricklehmann.checkout_lab.entities.products.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Optional;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Iterable<Product> listProducts() {
        return this.productRepository.findAll();
    }

    public Optional<Product> listProductBySku(String sku) {
        return this.productRepository.findBySku(sku);
    }

    public Iterable<Product> listProductBySku(Collection<String> skus) {
        return this.productRepository.findAllBySkuIn(skus);
    }

    public Product save(Product product) {
        return this.productRepository.save(product);
    }
}
