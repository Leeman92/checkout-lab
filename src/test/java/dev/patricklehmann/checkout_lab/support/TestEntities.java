package dev.patricklehmann.checkout_lab.support;

import dev.patricklehmann.checkout_lab.entities.orders.Order;
import dev.patricklehmann.checkout_lab.entities.orders.OrderItem;
import dev.patricklehmann.checkout_lab.entities.orders.OrderStatus;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptStatus;
import dev.patricklehmann.checkout_lab.entities.products.Product;
import dev.patricklehmann.checkout_lab.entities.shared.Money;
import dev.patricklehmann.checkout_lab.entities.shared.Sku;
import dev.patricklehmann.checkout_lab.entities.shared.StateMachine;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderTransitionException;
import dev.patricklehmann.checkout_lab.exceptions.payments.PaymentConflictException;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;

/**
 * Shared fixtures and state-machine builders for the fast unit tests. Centralizing them keeps every
 * test on the same fixed clock instant and the same transition tables the production {@code @Bean}s
 * define — so a test can never drift from the real machine it is meant to exercise.
 */
public final class TestEntities {

    /** The single fixed "now" all unit tests pin their {@code Clock} to. */
    public static final Instant NOW = Instant.parse("2026-07-22T10:00:00Z");

    private TestEntities() {}

    public static Product product(
            long id, String sku, long priceInCents, boolean active, int total, int reserved) {
        Product product = new Product();
        product.setSku(new Sku(sku));
        product.setName(sku);
        product.setNetPriceInCents(Money.ofCents(priceInCents));
        product.setActive(active);
        product.setTotalStock(total);
        product.setReservedStock(reserved);
        setId(product, id);
        return product;
    }

    public static Order reservedOrder(long id, String sku, int quantity) {
        Order order = new Order();
        order.setStatus(OrderStatus.RESERVED);
        order.setCurrency("EUR");
        order.setCreatedAt(NOW);
        order.setUpdatedAt(NOW);
        order.setIdempotencyKey("key-" + id);
        order.setRequestFingerprint("fp-" + id);
        order.setTotalNetInCents(Money.ofCents(3998));

        OrderItem item = new OrderItem();
        item.setSku(new Sku(sku));
        item.setQuantity(quantity);
        item.setUnitNetPriceInCents(Money.ofCents(1999));
        item.setLineNetInCents(Money.ofCents(1999L * quantity));
        order.addItem(item);

        setId(order, id);
        return order;
    }

    /** Mirrors the production {@code orderStateMachine} bean. */
    public static StateMachine<OrderStatus> orderStateMachine() {
        EnumMap<OrderStatus, EnumSet<OrderStatus>> transitions = new EnumMap<>(OrderStatus.class);
        transitions.put(OrderStatus.RESERVED, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED));
        transitions.put(OrderStatus.PAID, EnumSet.noneOf(OrderStatus.class));
        transitions.put(OrderStatus.PAYMENT_FAILED, EnumSet.noneOf(OrderStatus.class));
        transitions.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        return new StateMachine<>(transitions, OrderTransitionException::new);
    }

    /** Mirrors the production {@code paymentStateMachine} bean. */
    public static StateMachine<PaymentAttemptStatus> paymentStateMachine() {
        EnumMap<PaymentAttemptStatus, EnumSet<PaymentAttemptStatus>> transitions =
                new EnumMap<>(PaymentAttemptStatus.class);
        transitions.put(
                PaymentAttemptStatus.PENDING,
                EnumSet.of(PaymentAttemptStatus.DECLINED, PaymentAttemptStatus.SUCCESS));
        transitions.put(PaymentAttemptStatus.DECLINED, EnumSet.noneOf(PaymentAttemptStatus.class));
        transitions.put(PaymentAttemptStatus.SUCCESS, EnumSet.noneOf(PaymentAttemptStatus.class));
        return new StateMachine<>(transitions, PaymentConflictException::new);
    }

    /**
     * JPA assigns entity ids; in a pure unit test we set them by reflection to control fixtures.
     * Reflects the {@code id} field declared on the entity's own class.
     */
    public static void setId(Object entity, long id) {
        try {
            Field field = entity.getClass().getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not set entity id in test", exception);
        }
    }
}
