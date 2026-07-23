package dev.patricklehmann.checkout_lab;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import dev.patricklehmann.checkout_lab.controller.api.orders.dto.CreateOrderRequest;
import dev.patricklehmann.checkout_lab.controller.api.orders.dto.OrderItemRequest;
import dev.patricklehmann.checkout_lab.entities.orders.OrderRepository;
import dev.patricklehmann.checkout_lab.entities.products.Product;
import dev.patricklehmann.checkout_lab.entities.products.ProductRepository;
import dev.patricklehmann.checkout_lab.entities.shared.Money;
import dev.patricklehmann.checkout_lab.entities.shared.Sku;
import dev.patricklehmann.checkout_lab.services.orders.OrderCreationResult;
import dev.patricklehmann.checkout_lab.services.orders.OrderService;
import dev.patricklehmann.checkout_lab.support.IntegrationTest;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/**
 * Proves AC-020: if an unexpected error strikes mid-operation, the whole operation rolls back and
 * leaves no partial writes. {@code createOrder} reserves stock and then persists the order inside a
 * single transaction, so a failure at the persist step must also undo the reservation.
 *
 * <p>The failure is injected with {@link FailingOrderRepositoryConfig}: a {@code @Primary}
 * decorator around the real {@link OrderRepository} that throws on {@code save(...)} (after the
 * reservation has already run) and delegates every other call to the genuine repository.
 */
@Import(RollbackOnErrorTests.FailingOrderRepositoryConfig.class)
class RollbackOnErrorTests extends IntegrationTest {

    private static final String SKU = "TSHIRT-BLK-M";
    private static final int AVAILABLE_STOCK = 5;

    @Autowired private OrderService orderService;
    @Autowired private ProductRepository productRepository;

    @Test
    void failureAfterReservationRollsBackTheReservation() {
        seedProduct(AVAILABLE_STOCK);
        // In Case other tests have not cleared up by now!
        Integer orderCount = getOrderCount();
        Throwable throwable = null;
        try {
            OrderCreationResult orderCreationResult =
                    orderService.createOrder(UUID.randomUUID().toString(), orderForOneUnit());
        } catch (Throwable t) {
            throwable = t;
        }

        Throwable finalThrowable = throwable;
        assertAll(
                () -> assertInstanceOf(IllegalStateException.class, finalThrowable),
                () -> assertEquals(orderCount, getOrderCount()),
                () -> {
                    Product p = productRepository.findBySku(new Sku(SKU)).orElseThrow();
                    assertEquals(AVAILABLE_STOCK, p.getAvailableStock());
                    assertEquals(0, p.getReservedStock());
                    assertEquals(AVAILABLE_STOCK, p.getTotalStock());
                });
    }

    private Integer getOrderCount() {
        return jdbc.queryForObject("SELECT count(*) FROM orders", Integer.class);
    }

    private CreateOrderRequest orderForOneUnit() {
        return new CreateOrderRequest(List.of(new OrderItemRequest(SKU, 1)));
    }

    private String newKey() {
        return UUID.randomUUID().toString();
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

    /**
     * Registers a {@code @Primary} {@link OrderRepository} that fails on {@code save(...)} so the
     * order-creation transaction aborts after stock has been reserved. A JDK dynamic proxy keeps
     * the decorator to a single interesting line; every other method delegates to the real
     * repository (injected by its Spring Data bean name, {@code orderRepository}).
     */
    @TestConfiguration
    static class FailingOrderRepositoryConfig {

        @Bean
        @Primary
        OrderRepository failingOrderRepository(@Qualifier("orderRepository") OrderRepository real) {
            return (OrderRepository)
                    Proxy.newProxyInstance(
                            OrderRepository.class.getClassLoader(),
                            new Class<?>[] {OrderRepository.class},
                            (proxy, method, args) -> {
                                if ("save".equals(method.getName())) {
                                    throw new IllegalStateException(
                                            "Simulated failure after stock reservation");
                                }
                                try {
                                    return method.invoke(real, args);
                                } catch (InvocationTargetException exception) {
                                    throw exception.getCause();
                                }
                            });
        }
    }
}
