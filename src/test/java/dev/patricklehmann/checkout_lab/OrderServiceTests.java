package dev.patricklehmann.checkout_lab;

import static dev.patricklehmann.checkout_lab.support.TestEntities.NOW;
import static dev.patricklehmann.checkout_lab.support.TestEntities.orderStateMachine;
import static dev.patricklehmann.checkout_lab.support.TestEntities.product;
import static dev.patricklehmann.checkout_lab.support.TestEntities.reservedOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patricklehmann.checkout_lab.controller.api.orders.dto.CreateOrderRequest;
import dev.patricklehmann.checkout_lab.controller.api.orders.dto.OrderItemRequest;
import dev.patricklehmann.checkout_lab.entities.orders.Order;
import dev.patricklehmann.checkout_lab.entities.orders.OrderStatus;
import dev.patricklehmann.checkout_lab.entities.products.Product;
import dev.patricklehmann.checkout_lab.entities.shared.Money;
import dev.patricklehmann.checkout_lab.entities.shared.Sku;
import dev.patricklehmann.checkout_lab.exceptions.orders.IdempotencyConflictException;
import dev.patricklehmann.checkout_lab.exceptions.orders.InsufficientStockException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderNotFoundException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderTransitionException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderValidationException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderValidationReason;
import dev.patricklehmann.checkout_lab.services.orders.OrderCreationResult;
import dev.patricklehmann.checkout_lab.services.orders.OrderService;
import dev.patricklehmann.checkout_lab.support.FakeOrderRepository;
import dev.patricklehmann.checkout_lab.support.FakePaymentAttemptRepository;
import dev.patricklehmann.checkout_lab.support.FakeProductRepository;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class OrderServiceTests {

    private static final String KEY = "11111111-1111-1111-1111-111111111111";

    private final FakeOrderRepository orders = new FakeOrderRepository();
    private final FakeProductRepository products = new FakeProductRepository();
    private final FakePaymentAttemptRepository payments = new FakePaymentAttemptRepository();
    private final OrderService service =
            new OrderService(
                    orders,
                    products,
                    payments,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    orderStateMachine());

    @Test
    void createsReservedOrderAndReservesStock() {
        products.add(product(1L, "TSHIRT-BLK-M", 1999, true, 10, 0));

        OrderCreationResult result = service.createOrder(KEY, request(item("TSHIRT-BLK-M", 2)));

        assertThat(result.replay()).isFalse();

        Order order = result.order();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RESERVED);
        assertThat(order.getCurrency()).isEqualTo("EUR");
        assertThat(order.getCreatedAt()).isEqualTo(NOW);
        assertThat(order.getTotalNetInCents()).isEqualTo(Money.ofCents(3998));
        assertThat(order.getItems())
                .singleElement()
                .satisfies(
                        item -> {
                            assertThat(item.getSku()).isEqualTo(new Sku("TSHIRT-BLK-M"));
                            assertThat(item.getQuantity()).isEqualTo(2);
                            assertThat(item.getUnitNetPriceInCents())
                                    .isEqualTo(Money.ofCents(1999));
                            assertThat(item.getLineNetInCents()).isEqualTo(Money.ofCents(3998));
                        });

        assertThat(products.bySku.get(new Sku("TSHIRT-BLK-M")).getReservedStock()).isEqualTo(2);
    }

    @Test
    void sumsLineTotalsAcrossItems() {
        products.add(product(1L, "TSHIRT-BLK-M", 1999, true, 10, 0));
        products.add(product(2L, "HOODIE-GRY-M", 5999, true, 10, 0));

        OrderCreationResult result =
                service.createOrder(KEY, request(item("TSHIRT-BLK-M", 2), item("HOODIE-GRY-M", 1)));

        assertThat(result.order().getTotalNetInCents()).isEqualTo(Money.ofCents(2 * 1999 + 5999));
    }

    @Test
    void rejectsUnknownSkuWithoutReserving() {
        products.add(product(1L, "TSHIRT-BLK-M", 1999, true, 10, 0));

        assertThatThrownBy(
                        () ->
                                service.createOrder(
                                        KEY, request(item("TSHIRT-BLK-M", 1), item("NOPE", 1))))
                .isInstanceOf(OrderValidationException.class)
                .satisfies(
                        thrown -> {
                            OrderValidationException exception = (OrderValidationException) thrown;
                            assertThat(exception.getErrors())
                                    .singleElement()
                                    .satisfies(
                                            error -> {
                                                assertThat(error.sku()).isEqualTo("NOPE");
                                                assertThat(error.reason())
                                                        .isEqualTo(
                                                                OrderValidationReason.UNKNOWN_SKU);
                                            });
                        });

        assertThat(products.bySku.get(new Sku("TSHIRT-BLK-M")).getReservedStock()).isZero();
        assertThat(orders.saved).isEmpty();
    }

    @Test
    void rejectsInactiveProduct() {
        products.add(product(1L, "BELT-BRN-100", 3999, false, 10, 0));

        assertThatThrownBy(() -> service.createOrder(KEY, request(item("BELT-BRN-100", 1))))
                .isInstanceOf(OrderValidationException.class)
                .satisfies(
                        thrown ->
                                assertThat(((OrderValidationException) thrown).getErrors())
                                        .singleElement()
                                        .satisfies(
                                                error ->
                                                        assertThat(error.reason())
                                                                .isEqualTo(
                                                                        OrderValidationReason
                                                                                .INACTIVE_PRODUCT)));
    }

    @Test
    void accumulatesEveryValidationErrorInOnePass() {
        products.add(product(1L, "TSHIRT-BLK-M", 1999, true, 10, 0));
        products.add(product(2L, "BELT-BRN-100", 3999, false, 10, 0));

        assertThatThrownBy(
                        () ->
                                service.createOrder(
                                        KEY,
                                        request(
                                                item("TSHIRT-BLK-M", 1),
                                                item("TSHIRT-BLK-M", 1),
                                                item("BELT-BRN-100", 1),
                                                item("NOPE", 1))))
                .isInstanceOf(OrderValidationException.class)
                .satisfies(
                        thrown ->
                                assertThat(((OrderValidationException) thrown).getErrors())
                                        .extracting("sku", "reason")
                                        .containsExactlyInAnyOrder(
                                                org.assertj.core.groups.Tuple.tuple(
                                                        "TSHIRT-BLK-M",
                                                        OrderValidationReason.DUPLICATE_SKU),
                                                org.assertj.core.groups.Tuple.tuple(
                                                        "BELT-BRN-100",
                                                        OrderValidationReason.INACTIVE_PRODUCT),
                                                org.assertj.core.groups.Tuple.tuple(
                                                        "NOPE",
                                                        OrderValidationReason.UNKNOWN_SKU)));
    }

    @Test
    void rejectsInsufficientStockAccumulatingShortfalls() {
        products.add(product(1L, "TSHIRT-BLK-M", 1999, true, 1, 0));
        products.add(product(2L, "HOODIE-GRY-M", 5999, true, 3, 2));

        assertThatThrownBy(
                        () ->
                                service.createOrder(
                                        KEY,
                                        request(item("TSHIRT-BLK-M", 2), item("HOODIE-GRY-M", 2))))
                .isInstanceOf(InsufficientStockException.class)
                .satisfies(
                        thrown ->
                                assertThat(((InsufficientStockException) thrown).getShortfalls())
                                        .extracting("sku", "requested", "available")
                                        .containsExactlyInAnyOrder(
                                                org.assertj.core.groups.Tuple.tuple(
                                                        "TSHIRT-BLK-M", 2, 1),
                                                org.assertj.core.groups.Tuple.tuple(
                                                        "HOODIE-GRY-M", 2, 1)));

        assertThat(products.bySku.get(new Sku("TSHIRT-BLK-M")).getReservedStock()).isZero();
    }

    @Test
    void keepsPriceStableWhenProductPriceChangesLater() {
        Product product = product(1L, "TSHIRT-BLK-M", 1999, true, 10, 0);
        products.add(product);

        Order order = service.createOrder(KEY, request(item("TSHIRT-BLK-M", 2))).order();
        Money originalTotal = order.getTotalNetInCents();

        product.setNetPriceInCents(Money.ofCents(9999));

        assertThat(order.getTotalNetInCents())
                .isEqualTo(originalTotal)
                .isEqualTo(Money.ofCents(3998));
    }

    @Test
    void replaysSameKeyWithoutReservingAgain() {
        products.add(product(1L, "TSHIRT-BLK-M", 1999, true, 10, 0));
        CreateOrderRequest request = request(item("TSHIRT-BLK-M", 2));

        Order first = service.createOrder(KEY, request).order();
        OrderCreationResult replay = service.createOrder(KEY, request);

        assertThat(replay.replay()).isTrue();
        assertThat(replay.order()).isSameAs(first);
        assertThat(products.bySku.get(new Sku("TSHIRT-BLK-M")).getReservedStock()).isEqualTo(2);
        assertThat(orders.saved).hasSize(1);
    }

    @Test
    void rejectsSameKeyWithDifferentContent() {
        products.add(product(1L, "TSHIRT-BLK-M", 1999, true, 10, 0));

        service.createOrder(KEY, request(item("TSHIRT-BLK-M", 2)));

        assertThatThrownBy(() -> service.createOrder(KEY, request(item("TSHIRT-BLK-M", 3))))
                .isInstanceOf(IdempotencyConflictException.class);

        assertThat(products.bySku.get(new Sku("TSHIRT-BLK-M")).getReservedStock()).isEqualTo(2);
        assertThat(orders.saved).hasSize(1);
    }

    @Test
    void getOrderReturnsStoredOrder() {
        Order order = new Order();
        orders.byId.put(42L, order);

        assertThat(service.getOrder(42L)).isSameAs(order);
    }

    @Test
    void getOrderThrowsWhenMissing() {
        assertThatThrownBy(() -> service.getOrder(999L))
                .isInstanceOf(OrderNotFoundException.class)
                .extracting("orderId")
                .isEqualTo(999L);
    }

    @Test
    void cancelsReservedOrderAndReleasesStock() {
        products.add(product(1L, "TSHIRT-BLK-M", 1999, true, 10, 2));
        Order order = reservedOrder(1L, "TSHIRT-BLK-M", 2);
        orders.byId.put(1L, order);

        Order result = service.cancelOrder(1L);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.getUpdatedAt()).isEqualTo(NOW);
        assertThat(products.bySku.get(new Sku("TSHIRT-BLK-M")).getReservedStock()).isZero();
    }

    @Test
    void repeatedCancelDoesNotReleaseStockTwice() {
        products.add(product(1L, "TSHIRT-BLK-M", 1999, true, 10, 2));
        Order order = reservedOrder(1L, "TSHIRT-BLK-M", 2);
        orders.byId.put(1L, order);

        service.cancelOrder(1L);
        service.cancelOrder(1L);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(products.bySku.get(new Sku("TSHIRT-BLK-M")).getReservedStock()).isZero();
    }

    @Test
    void cannotCancelPaidOrder() {
        products.add(product(1L, "TSHIRT-BLK-M", 1999, true, 10, 2));
        Order order = reservedOrder(1L, "TSHIRT-BLK-M", 2);
        order.setStatus(OrderStatus.PAID);
        orders.byId.put(1L, order);

        assertThatThrownBy(() -> service.cancelOrder(1L))
                .isInstanceOf(OrderTransitionException.class);
        assertThat(products.bySku.get(new Sku("TSHIRT-BLK-M")).getReservedStock()).isEqualTo(2);
    }

    @Test
    void cancelUnknownOrderThrows() {
        assertThatThrownBy(() -> service.cancelOrder(999L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    private static CreateOrderRequest request(OrderItemRequest... items) {
        return new CreateOrderRequest(List.of(items));
    }

    private static OrderItemRequest item(String sku, int quantity) {
        return new OrderItemRequest(sku, quantity);
    }
}
