package dev.patricklehmann.checkout_lab.controller.api.products;

import dev.patricklehmann.checkout_lab.entities.products.Product;
import dev.patricklehmann.checkout_lab.entities.shared.Money;
import dev.patricklehmann.checkout_lab.entities.shared.Sku;
import dev.patricklehmann.checkout_lab.services.products.ProductService;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Development-only convenience endpoint that seeds a catalogue of sample products. Guarded by
 * {@code @Profile("development")} — the profile the local Docker stack activates — so the bean does
 * not exist in the production image and the route returns 404 there. This is what closes the
 * "remove or guard the seeding endpoint before production" item from the requirements ledger.
 */
@RestController
@RequestMapping("/products")
@Profile("development")
public class DevDataController {

    private final ProductService productService;

    public DevDataController(ProductService productService) {
        this.productService = productService;
    }

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

        product.setSku(new Sku(sku));
        product.setName(name);
        product.setNetPriceInCents(Money.ofCents(price));
        product.setActive(active);
        product.setTotalStock(totalStock);
        product.setReservedStock(reservedStock);

        return product;
    }
}
