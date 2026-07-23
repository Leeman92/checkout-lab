package dev.patricklehmann.checkout_lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.patricklehmann.checkout_lab.controller.api.ApiResponseMetadataAdvice;
import dev.patricklehmann.checkout_lab.controller.api.products.DevDataController;
import dev.patricklehmann.checkout_lab.entities.products.Product;
import dev.patricklehmann.checkout_lab.exceptions.GlobalExceptionHandler;
import dev.patricklehmann.checkout_lab.exceptions.product.ProductAlreadyExistsException;
import dev.patricklehmann.checkout_lab.filters.RequestIdFilter;
import dev.patricklehmann.checkout_lab.services.products.ProductService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Web-layer tests for the development-only seeding endpoint. The endpoint itself is guarded by
 * {@code @Profile("development")} in production; here it is instantiated directly so the seed shape
 * and its conflict handling stay covered.
 */
class DevDataControllerTests {

    private MockMvc mockMvc;
    private StubProductService productService;

    @BeforeEach
    void setUpMvc() {
        productService = new StubProductService();
        mockMvc =
                MockMvcBuilders.standaloneSetup(new DevDataController(productService))
                        .setControllerAdvice(
                                new ApiResponseMetadataAdvice(), new GlobalExceptionHandler())
                        .addFilters(new RequestIdFilter())
                        .build();
    }

    @Test
    void generatesTheDocumentedSeedProductsWithValidStock() throws Exception {
        mockMvc.perform(post("/products/testdata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(12));

        assertThat(productService.savedProducts)
                .extracting(product -> product.getSku().value())
                .containsExactly(
                        "TSHIRT-BLK-M",
                        "TSHIRT-WHT-L",
                        "HOODIE-GRY-M",
                        "HOODIE-BLK-XL",
                        "JEANS-BLU-32",
                        "SNEAKER-WHT-42",
                        "CAP-NVY-UNI",
                        "SOCKS-BLK-3P",
                        "BELT-BRN-100",
                        "JACKET-GRN-L",
                        "BAG-BLK-20L",
                        "WATCH-SLV-01");

        assertThat(productService.savedProducts)
                .allSatisfy(
                        product -> {
                            assertThat(product.getTotalStock()).isBetween(0, 100);
                            assertThat(product.getReservedStock())
                                    .isBetween(0, product.getTotalStock());
                        });
    }

    @Test
    void returnsConflictWhenSeedProductsAlreadyExist() throws Exception {
        productService.failure = new ProductAlreadyExistsException();

        mockMvc.perform(post("/products/testdata"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:problem:product-already-exists"))
                .andExpect(jsonPath("$.title").value("Product already exists"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(
                        jsonPath("$.detail")
                                .value("A product with the supplied SKU already exists."));
    }

    static final class StubProductService extends ProductService {

        private List<Product> savedProducts = List.of();
        private RuntimeException failure;

        private StubProductService() {
            super(null);
        }

        @Override
        public Iterable<Product> saveAll(List<Product> products) {
            savedProducts = List.copyOf(products);
            if (failure != null) {
                throw failure;
            }
            return savedProducts;
        }
    }
}
