package dev.patricklehmann.checkout_lab;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.patricklehmann.checkout_lab.controller.api.orders.dto.CreateOrderRequest;
import dev.patricklehmann.checkout_lab.controller.api.orders.dto.OrderItemRequest;
import dev.patricklehmann.checkout_lab.entities.products.Product;
import dev.patricklehmann.checkout_lab.entities.products.ProductRepository;
import dev.patricklehmann.checkout_lab.entities.shared.Money;
import dev.patricklehmann.checkout_lab.entities.shared.Sku;
import dev.patricklehmann.checkout_lab.exceptions.orders.InsufficientStockException;
import dev.patricklehmann.checkout_lab.services.orders.OrderService;
import dev.patricklehmann.checkout_lab.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Proves FR-010 / AC-009: when many buyers race for the last unit, the atomic conditional {@code
 * tryReserveStock} UPDATE lets exactly one win — the stock is never oversold. This is a real
 * database race across committed transactions, which the in-memory fakes cannot reproduce.
 */
class OrderConcurrencyTests extends IntegrationTest {

    private static final String SKU = "TSHIRT-BLK-M";

    @Autowired private OrderService orderService;
    @Autowired private ProductRepository productRepository;

    @Test
    void racingForTheLastUnitLetsExactlyOneWin() {
        int competitors = 8;

        seedProduct(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(competitors)) {
            CountDownLatch ready = new CountDownLatch(competitors);
            CountDownLatch start = new CountDownLatch(1);

            try {
                List<Future<Throwable>> futures = new ArrayList<>();
                for (int i = 0; i < competitors; i++) {
                    futures.add(
                            executor.submit(() -> attemptConcurrentOrderCreation(ready, start)));
                }

                // Wait until all eight threads are ready, then release them together.
                ready.await();
                start.countDown();

                List<Throwable> outcomes = new ArrayList<>();

                for (Future<Throwable> future : futures) {
                    outcomes.add(future.get());
                }

                long successes = outcomes.stream().filter(Objects::isNull).count();

                List<Throwable> failures = outcomes.stream().filter(Objects::nonNull).toList();

                assertAll(
                        () -> assertEquals(1, successes),
                        () -> assertEquals(7, failures.size()),
                        () ->
                                assertTrue(
                                        failures.stream()
                                                .allMatch(
                                                        exception ->
                                                                exception
                                                                        instanceof
                                                                        InsufficientStockException),
                                        () -> "Unexpected exceptions: " + failures),
                        () -> {
                            Product p = productRepository.findBySku(new Sku(SKU)).orElseThrow();
                            assertEquals(0, p.getAvailableStock());
                            assertEquals(1, p.getReservedStock());
                        });
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private Throwable attemptConcurrentOrderCreation(CountDownLatch ready, CountDownLatch start)
            throws InterruptedException {
        ready.countDown();
        start.await();

        try {
            orderService.createOrder(UUID.randomUUID().toString(), orderForOneUnit());

            return null; // success
        } catch (Throwable exception) {
            return exception;
        }
    }

    private CreateOrderRequest orderForOneUnit() {
        return new CreateOrderRequest(List.of(new OrderItemRequest(SKU, 1)));
    }

    void seedProduct(int totalStock) {
        Product product = new Product();
        product.setSku(new Sku(SKU));
        product.setName("Basic T-Shirt Schwarz M");
        product.setNetPriceInCents(Money.ofCents(1999));
        product.setActive(true);
        product.setTotalStock(totalStock);
        product.setReservedStock(0);
        productRepository.save(product);
    }
}
