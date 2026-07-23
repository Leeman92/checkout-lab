package dev.patricklehmann.checkout_lab.controller.api.products;

import dev.patricklehmann.checkout_lab.entities.products.Product;
import dev.patricklehmann.checkout_lab.entities.shared.Sku;
import dev.patricklehmann.checkout_lab.services.products.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for listing, looking up, and creating products. A thin transport layer that
 * delegates persistence and conflict handling to {@link ProductService}.
 */
@RestController
@RequestMapping("/products")
public class ProductsController {

    private final ProductService productService;

    public ProductsController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping({"", "/"})
    public Iterable<Product> listProducts(HttpServletRequest request) {
        return this.productService.listProducts();
    }

    @GetMapping({"/{sku}", "/{sku}/"})
    public Product getProductBySku(@PathVariable String sku) {
        return this.productService.listProductBySku(new Sku(sku));
    }

    @PostMapping({"", "/"})
    public Product addOneProduct(@RequestBody Product product) {
        return this.productService.save(product);
    }
}
