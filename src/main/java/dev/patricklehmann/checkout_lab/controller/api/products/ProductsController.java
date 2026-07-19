package dev.patricklehmann.checkout_lab.controller.api.products;

import dev.patricklehmann.checkout_lab.controller.api.ApiController;
import dev.patricklehmann.checkout_lab.entities.products.Product;
import dev.patricklehmann.checkout_lab.services.products.ProductService;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
public class ProductsController extends ApiController {

    private final ProductService productService;

    public ProductsController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping({"", "/"})
    public ResponseEntity<Map<String, Object>> listProducts() {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("products", this.productService.listProducts());

        return this.sendResponse(body, HttpStatus.valueOf(200));
    }

    @GetMapping({"/{sku}", "/{sku}/"})
    public ResponseEntity<Map<String, Object>> getProductBySku(@PathVariable String sku) {
        Map<String, Object> body = new LinkedHashMap<>();
        Optional<Product> product = this.productService.listProductBySku(sku);
        int statusCode = 200;
        if (product.isEmpty()) {
            statusCode = 404;
        }


        body.put("product", product);

        return this.sendResponse(body, HttpStatus.valueOf(statusCode));
    }

    @PostMapping({"", "/"})
    public Product addOneProduct(@RequestBody Product product) {
        return this.productService.save(product);
    }
}
