package dev.patricklehmann.checkout_lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patricklehmann.checkout_lab.controller.api.orders.dto.CreateOrderRequest;
import dev.patricklehmann.checkout_lab.controller.api.orders.dto.OrderItemRequest;
import dev.patricklehmann.checkout_lab.entities.orders.Order;
import dev.patricklehmann.checkout_lab.entities.orders.OrderRepository;
import dev.patricklehmann.checkout_lab.entities.orders.OrderStatus;
import dev.patricklehmann.checkout_lab.entities.products.Product;
import dev.patricklehmann.checkout_lab.entities.products.ProductRepository;
import dev.patricklehmann.checkout_lab.entities.shared.Money;
import dev.patricklehmann.checkout_lab.entities.shared.Sku;
import dev.patricklehmann.checkout_lab.exceptions.orders.IdempotencyConflictException;
import dev.patricklehmann.checkout_lab.exceptions.orders.InsufficientStockException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderNotFoundException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderValidationException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderValidationReason;
import dev.patricklehmann.checkout_lab.services.orders.OrderCreationResult;
import dev.patricklehmann.checkout_lab.services.orders.OrderService;
import java.lang.reflect.Field;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OrderServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-22T10:00:00Z");
    private static final String KEY = "11111111-1111-1111-1111-111111111111";

    private final FakeOrderRepository orders = new FakeOrderRepository();
    private final FakeProductRepository products = new FakeProductRepository();
    private final OrderService service =
            new OrderService(orders, products, Clock.fixed(NOW, ZoneOffset.UTC));

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

    private static CreateOrderRequest request(OrderItemRequest... items) {
        return new CreateOrderRequest(List.of(items));
    }

    private static OrderItemRequest item(String sku, int quantity) {
        return new OrderItemRequest(sku, quantity);
    }

    private static Product product(
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

    /**
     * JPA assigns entity ids; in a pure unit test we set them by reflection to control fixtures.
     */
    private static void setId(Product product, long id) {
        try {
            Field field = Product.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(product, id);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not set product id in test", exception);
        }
    }

    private static final class FakeProductRepository implements ProductRepository {

        private final Map<Sku, Product> bySku = new LinkedHashMap<>();
        private final Map<Long, Product> byId = new LinkedHashMap<>();

        void add(Product product) {
            bySku.put(product.getSku(), product);
            byId.put(product.getId(), product);
        }

        @Override
        public Optional<Product> findBySku(Sku sku) {
            return Optional.ofNullable(bySku.get(sku));
        }

        @Override
        public List<Product> findAllBySkuIn(Collection<Sku> skus) {
            return skus.stream().map(bySku::get).filter(product -> product != null).toList();
        }

        @Override
        public int tryReserveStock(Long productId, int quantity) {
            Product product = byId.get(productId);
            if (product != null && product.getAvailableStock() >= quantity) {
                product.setReservedStock(product.getReservedStock() + quantity);
                return 1;
            }
            return 0;
        }

        @Override
        public <S extends Product> S save(S entity) {
            add(entity);
            return entity;
        }

        @Override
        public <S extends Product> Iterable<S> saveAll(Iterable<S> entities) {
            List<S> saved = new ArrayList<>();
            entities.forEach(
                    entity -> {
                        add(entity);
                        saved.add(entity);
                    });
            return saved;
        }

        @Override
        public Optional<Product> findById(Long id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public boolean existsById(Long id) {
            return byId.containsKey(id);
        }

        @Override
        public Iterable<Product> findAll() {
            return bySku.values();
        }

        @Override
        public Iterable<Product> findAllById(Iterable<Long> ids) {
            List<Product> matches = new ArrayList<>();
            ids.forEach(id -> findById(id).ifPresent(matches::add));
            return matches;
        }

        @Override
        public long count() {
            return bySku.size();
        }

        @Override
        public void deleteById(Long id) {
            findById(id).ifPresent(this::delete);
        }

        @Override
        public void delete(Product entity) {
            bySku.remove(entity.getSku());
            byId.remove(entity.getId());
        }

        @Override
        public void deleteAllById(Iterable<? extends Long> ids) {
            ids.forEach(this::deleteById);
        }

        @Override
        public void deleteAll(Iterable<? extends Product> entities) {
            entities.forEach(this::delete);
        }

        @Override
        public void deleteAll() {
            bySku.clear();
            byId.clear();
        }
    }

    private static final class FakeOrderRepository implements OrderRepository {

        private final List<Order> saved = new ArrayList<>();
        private final Map<Long, Order> byId = new LinkedHashMap<>();

        @Override
        public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
            return saved.stream()
                    .filter(order -> idempotencyKey.equals(order.getIdempotencyKey()))
                    .findFirst();
        }

        @Override
        public <S extends Order> S save(S entity) {
            saved.add(entity);
            return entity;
        }

        @Override
        public <S extends Order> Iterable<S> saveAll(Iterable<S> entities) {
            List<S> result = new ArrayList<>();
            entities.forEach(
                    entity -> {
                        saved.add(entity);
                        result.add(entity);
                    });
            return result;
        }

        @Override
        public Optional<Order> findById(Long id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public boolean existsById(Long id) {
            return byId.containsKey(id);
        }

        @Override
        public Iterable<Order> findAll() {
            return saved;
        }

        @Override
        public Iterable<Order> findAllById(Iterable<Long> ids) {
            List<Order> matches = new ArrayList<>();
            ids.forEach(id -> findById(id).ifPresent(matches::add));
            return matches;
        }

        @Override
        public long count() {
            return saved.size();
        }

        @Override
        public void deleteById(Long id) {
            byId.remove(id);
        }

        @Override
        public void delete(Order entity) {
            saved.remove(entity);
        }

        @Override
        public void deleteAllById(Iterable<? extends Long> ids) {
            ids.forEach(this::deleteById);
        }

        @Override
        public void deleteAll(Iterable<? extends Order> entities) {
            entities.forEach(this::delete);
        }

        @Override
        public void deleteAll() {
            saved.clear();
            byId.clear();
        }
    }
}
