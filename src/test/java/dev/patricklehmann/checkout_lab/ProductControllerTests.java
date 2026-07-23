package dev.patricklehmann.checkout_lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.patricklehmann.checkout_lab.controller.api.ApiResponseMetadataAdvice;
import dev.patricklehmann.checkout_lab.controller.api.products.ProductsController;
import dev.patricklehmann.checkout_lab.entities.products.Product;
import dev.patricklehmann.checkout_lab.entities.shared.Money;
import dev.patricklehmann.checkout_lab.entities.shared.Sku;
import dev.patricklehmann.checkout_lab.exceptions.GlobalExceptionHandler;
import dev.patricklehmann.checkout_lab.exceptions.product.ProductNotFoundException;
import dev.patricklehmann.checkout_lab.filters.RequestIdFilter;
import dev.patricklehmann.checkout_lab.services.products.ProductService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProductControllerTests {

    private static final String REQUEST_ID = "59edc107-a937-40e0-b387-3d342053a238";

    private MockMvc mockMvc;
    private StubProductService productService;

    @BeforeEach
    void setUpMvc() {
        productService = new StubProductService();
        mockMvc =
                MockMvcBuilders.standaloneSetup(new ProductsController(productService))
                        .setControllerAdvice(
                                new ApiResponseMetadataAdvice(), new GlobalExceptionHandler())
                        .addFilters(new RequestIdFilter())
                        .build();
    }

    @Test
    void returnsProductListWithResponseMetadata() throws Exception {
        Product product = product("TSHIRT-BLK-M", "Basic T-Shirt", 1999, true);
        productService.products = List.of(product);

        mockMvc.perform(get("/products").header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].sku").value("TSHIRT-BLK-M"))
                .andExpect(jsonPath("$.data[0].name").value("Basic T-Shirt"))
                .andExpect(jsonPath("$.data[0].netPriceInCents").value(1999))
                .andExpect(jsonPath("$.data[0].active").value(true));
    }

    @Test
    void normalizesSkuBeforeLookingUpSingleProduct() throws Exception {
        productService.product = product("TSHIRT-BLK-M", "Basic T-Shirt", 1999, true);

        mockMvc.perform(get("/products/tshirt-blk-m"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value("TSHIRT-BLK-M"));

        assertThat(productService.requestedSku).isEqualTo(new Sku("TSHIRT-BLK-M"));
    }

    @Test
    void createsProductFromJsonRequest() throws Exception {
        productService.productToReturn = product("HOODIE-GRY-M", "Premium Hoodie", 5999, true);

        mockMvc.perform(
                        post("/products")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "sku": "HOODIE-GRY-M",
                                          "name": "Premium Hoodie",
                                          "netPriceInCents": 5999,
                                          "active": true,
                                          "totalStock": 20,
                                          "reservedStock": 3
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sku").value("HOODIE-GRY-M"));

        assertThat(productService.savedProduct.getSku()).isEqualTo(new Sku("HOODIE-GRY-M"));
        assertThat(productService.savedProduct.getNetPriceInCents()).isEqualTo(Money.ofCents(5999));
        assertThat(productService.savedProduct.getTotalStock()).isEqualTo(20);
        assertThat(productService.savedProduct.getReservedStock()).isEqualTo(3);
    }

    @Test
    void returnsProblemDetailsWhenProductDoesNotExist() throws Exception {
        productService.failure = new ProductNotFoundException("MISSING-SKU");

        mockMvc.perform(
                        get("/products/missing-sku")
                                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andExpect(jsonPath("$.type").value("urn:problem:product-not-found"))
                .andExpect(jsonPath("$.title").value("Product not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(
                        jsonPath("$.detail")
                                .value("Could not find product with given SKU 'MISSING-SKU'"))
                .andExpect(jsonPath("$.instance").value("/products/missing-sku"))
                .andExpect(jsonPath("$.sku").value("MISSING-SKU"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void returnsFrameworkProblemDetailsWhenRouteDoesNotExist() throws Exception {
        mockMvc.perform(get("/missing-route").header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andExpect(jsonPath("$.type").value("urn:problem:not-found"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").isString())
                .andExpect(jsonPath("$.instance").value("/missing-route"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void returnsSanitizedProblemDetailsForUnexpectedErrors() throws Exception {
        productService.failure = new IllegalStateException("sensitive database detail");

        mockMvc.perform(get("/products").header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.type").value("urn:problem:internal-server-error"))
                .andExpect(jsonPath("$.title").value("Internal Server Error"))
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.detail", not(containsString("sensitive database detail"))))
                .andExpect(jsonPath("$.instance").value("/products"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID));
    }

    @Test
    void replacesInvalidRequestIdAndUsesItConsistently() throws Exception {
        productService.products = List.of();

        MvcResult result =
                mockMvc.perform(
                                get("/products")
                                        .header(
                                                RequestIdFilter.HEADER_NAME,
                                                "not-a-valid-request-id"))
                        .andExpect(status().isOk())
                        .andReturn();

        String generatedRequestId = result.getResponse().getHeader(RequestIdFilter.HEADER_NAME);

        assertThat(generatedRequestId).isNotNull();
        assertThat(UUID.fromString(generatedRequestId).toString()).isEqualTo(generatedRequestId);
        assertThat(result.getResponse().getContentAsString())
                .contains("\"requestId\":\"%s\"".formatted(generatedRequestId));
    }

    private static Product product(String sku, String name, long price, boolean active) {
        Product product = new Product();
        product.setSku(new Sku(sku));
        product.setName(name);
        product.setNetPriceInCents(Money.ofCents(price));
        product.setActive(active);
        return product;
    }

    static final class StubProductService extends ProductService {

        private Iterable<Product> products;
        private Product product;
        private Product productToReturn;
        private Product savedProduct;
        private Sku requestedSku;
        private RuntimeException failure;

        private StubProductService() {
            super(null);
            products = List.of();
            product = new Product();
            productToReturn = new Product();
            savedProduct = null;
            requestedSku = null;
            failure = null;
        }

        @Override
        public Iterable<Product> listProducts() {
            throwFailureIfConfigured();
            return products;
        }

        @Override
        public Product listProductBySku(Sku sku) {
            requestedSku = sku;
            throwFailureIfConfigured();
            return product;
        }

        @Override
        public Product save(Product product) {
            savedProduct = product;
            throwFailureIfConfigured();
            return productToReturn;
        }

        private void throwFailureIfConfigured() {
            if (failure != null) {
                throw failure;
            }
        }
    }
}
