package dev.patricklehmann.checkout_lab.controller.api.products;

import dev.patricklehmann.checkout_lab.entities.products.Product;
import dev.patricklehmann.checkout_lab.services.products.ProductService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        return this.productService.listProductBySku(sku.toUpperCase(Locale.ROOT));
    }

    @PostMapping({"", "/"})
    public Product addOneProduct(@RequestBody Product product) {
        return this.productService.save(product);
    }

    // This endpoint is purely to fill initially some data into the system so that I can start out
    // with some data.
    // For a production ready release this would be removed
    @PostMapping({"testdata", "testdata/"})
    public Iterable<Product> addTestProducts() {
        List<Product> products =
                List.of(
                        createProduct("TSHIRT-BLK-M", "Basic T-Shirt Schwarz M", 1999, true),
                        createProduct("TSHIRT-WHT-L", "Basic T-Shirt Weiß L", 1999, true),
                        createProduct("HOODIE-GRY-M", "Premium Hoodie Grau M", 5999, true),
                        createProduct("HOODIE-BLK-XL", "Premium Hoodie Schwarz XL", 6499, true),
                        createProduct("JEANS-BLU-32", "Regular Fit Jeans Blau 32", 7999, true),
                        createProduct("SNEAKER-WHT-42", "Classic Sneaker Weiß 42", 8999, true),
                        createProduct("CAP-NVY-UNI", "Baseball Cap Navy", 2499, true),
                        createProduct("SOCKS-BLK-3P", "Socken Schwarz 3er-Pack", 1499, true),
                        createProduct("BELT-BRN-100", "Ledergürtel Braun 100 cm", 3999, false),
                        createProduct("JACKET-GRN-L", "Übergangsjacke Grün L", 10999, true),
                        createProduct("BAG-BLK-20L", "Rucksack Schwarz 20 Liter", 6999, true),
                        createProduct("WATCH-SLV-01", "Armbanduhr Silber", 12999, false));

        return this.productService.saveAll(products);
    }

    private Product createProduct(String sku, String name, long price, boolean active) {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int totalStock = random.nextInt(0, 101);
        int reservedStock = random.nextInt(0, totalStock + 1);

        Product product = new Product();

        product.setSku(sku);
        product.setName(name);
        product.setNetPriceInCents(price);
        product.setActive(active);
        product.setTotalStock(totalStock);
        product.setReservedStock(reservedStock);

        return product;
    }
}
