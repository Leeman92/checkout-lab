package dev.patricklehmann.checkout_lab;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.patricklehmann.checkout_lab.controller.api.orders.dto.CreateOrderRequest;
import dev.patricklehmann.checkout_lab.controller.api.orders.dto.OrderItemRequest;
import dev.patricklehmann.checkout_lab.entities.orders.Order;
import dev.patricklehmann.checkout_lab.entities.orders.OrderRepository;
import dev.patricklehmann.checkout_lab.entities.orders.OrderStatus;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptStatus;
import dev.patricklehmann.checkout_lab.entities.products.Product;
import dev.patricklehmann.checkout_lab.entities.products.ProductRepository;
import dev.patricklehmann.checkout_lab.entities.shared.Money;
import dev.patricklehmann.checkout_lab.entities.shared.Sku;
import dev.patricklehmann.checkout_lab.services.orders.OrderService;
import dev.patricklehmann.checkout_lab.services.payments.PaymentService;
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
 * Proves FR-024: when several requests race to start a payment on the same reserved order, the
 * {@code uq(order_id, attempt_number)} constraint (plus {@code @Version} on the order) lets exactly
 * one attempt survive — the order is settled once, never double-paid.
 */
class PaymentConcurrencyTests extends IntegrationTest {

    private static final String SKU = "TSHIRT-BLK-M";

    @Autowired private OrderService orderService;
    @Autowired private PaymentService paymentService;
    @Autowired private ProductRepository productRepository;
    @Autowired private OrderRepository orderRepository;

    private Throwable attemptConcurrentPayment(
            long orderId, CountDownLatch start, CountDownLatch ready) throws InterruptedException {
        ready.countDown();
        start.await();
        try {
            paymentService.startPayment(orderId, PaymentAttemptStatus.SUCCESS);
            return null;
        } catch (Exception ex) {
            return ex;
        }
    }

    @Test
    void racingToStartPaymentSettlesTheOrderExactlyOnce() {
        long orderId = givenReservedOrder();
        int contenders = 8;

        try (ExecutorService executorService = Executors.newFixedThreadPool(contenders)) {
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch ready = new CountDownLatch(contenders);

            try {
                List<Future<Throwable>> tasks = new ArrayList<>();
                for (int i = 0; i < contenders; i++) {
                    tasks.add(
                            executorService.submit(
                                    () -> attemptConcurrentPayment(orderId, start, ready)));
                }

                ready.await();
                start.countDown();

                List<Throwable> outcomes = new ArrayList<>();
                for (Future<Throwable> task : tasks) {
                    outcomes.add(task.get());
                }

                long successes = outcomes.stream().filter(Objects::isNull).count();
                long failures = outcomes.stream().filter(Objects::nonNull).count();

                assertAll(
                        () -> assertEquals(1, successes),
                        () -> assertEquals(7, failures),
                        () -> {
                            Order order = orderRepository.findById(orderId).orElseThrow();
                            assertEquals(OrderStatus.PAID, order.getStatus());
                        },
                        () -> {
                            Integer entryCount =
                                    jdbc.queryForObject(
                                            "SELECT count(*) FROM payment_attempts WHERE order_id = ?",
                                            Integer.class,
                                            orderId);

                            assertEquals(1, entryCount);
                        });
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                executorService.shutdownNow();
            }
        }
    }

    /** Persists a committed RESERVED order (via the real reservation flow) and returns its id. */
    private long givenReservedOrder() {
        seedProduct(5);
        return orderService
                .createOrder(UUID.randomUUID().toString(), orderForOneUnit())
                .order()
                .getId();
    }

    private CreateOrderRequest orderForOneUnit() {
        return new CreateOrderRequest(List.of(new OrderItemRequest(SKU, 1)));
    }

    private void seedProduct(int totalStock) {
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
