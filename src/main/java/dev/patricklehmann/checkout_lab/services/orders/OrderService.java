package dev.patricklehmann.checkout_lab.services.orders;

import dev.patricklehmann.checkout_lab.controller.api.orders.dto.CreateOrderRequest;
import dev.patricklehmann.checkout_lab.controller.api.orders.dto.OrderItemRequest;
import dev.patricklehmann.checkout_lab.entities.orders.Order;
import dev.patricklehmann.checkout_lab.entities.orders.OrderItem;
import dev.patricklehmann.checkout_lab.entities.orders.OrderRepository;
import dev.patricklehmann.checkout_lab.entities.orders.OrderStatus;
import dev.patricklehmann.checkout_lab.entities.products.Product;
import dev.patricklehmann.checkout_lab.entities.products.ProductRepository;
import dev.patricklehmann.checkout_lab.entities.shared.Money;
import dev.patricklehmann.checkout_lab.entities.shared.Sku;
import dev.patricklehmann.checkout_lab.exceptions.orders.IdempotencyConflictException;
import dev.patricklehmann.checkout_lab.exceptions.orders.InsufficientStockException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderNotFoundException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderValidationError;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderValidationException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderValidationReason;
import dev.patricklehmann.checkout_lab.exceptions.orders.StockShortfall;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core order use cases: reading an order and creating one with stock reservation.
 *
 * <p>Creation is idempotent (keyed on the client's idempotency key) and transactional, following a
 * strict validate-all-then-mutate discipline so a rejected request leaves no partial effects
 * (FR-004 / ER-004).
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    static final String CURRENCY = "EUR";

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final Clock clock;

    /**
     * Reads an order by id.
     *
     * @throws OrderNotFoundException if no order with that id exists
     */
    @Transactional(readOnly = true)
    public Order getOrder(long orderId) {
        return orderRepository
                .findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /**
     * Creates an order under the given idempotency key, reserving stock for every line atomically.
     *
     * <p>Idempotency is resolved first: if the key was seen before and the request fingerprint
     * matches, the stored order is returned as a replay (FR-011); if the key matches but the
     * content differs, an {@link IdempotencyConflictException} is thrown (FR-012). Otherwise the
     * order is validated in full (batched via {@link #buildReservedOrder}) and stock is reserved
     * per line (batched via {@link #reserveStock}). The whole method is transactional, so any
     * failure — validation, conflict, or stock shortfall — rolls back all reservations, leaving no
     * partial order (FR-004 / ER-004).
     *
     * @return a result flagged {@code replay=false} for a freshly created order (HTTP 201) or
     *     {@code replay=true} for a returned existing order (HTTP 200)
     */
    @Transactional
    public OrderCreationResult createOrder(String idempotencyKey, CreateOrderRequest request) {
        String fingerprint = canonicalFingerprint(request);
        Optional<Order> existingOrder = orderRepository.findByIdempotencyKey(idempotencyKey);

        if (existingOrder.isPresent()) {
            Order persistedOrder = existingOrder.get();

            if (Objects.equals(persistedOrder.getRequestFingerprint(), fingerprint)) {
                return new OrderCreationResult(persistedOrder, true);
            }

            throw new IdempotencyConflictException(idempotencyKey);
        }

        Map<Sku, Product> productsBySku = loadProductsBySku(request);
        Order order = buildReservedOrder(idempotencyKey, fingerprint, request, productsBySku);
        reserveStock(order, productsBySku);

        orderRepository.save(order);
        return new OrderCreationResult(order, false);
    }

    /**
     * Attempts an atomic conditional reservation for every line, accumulating any lines that could
     * not be satisfied. All lines are attempted before failing so the client sees every shortfall
     * at once in a single batched {@link InsufficientStockException} (FR-009 / FR-010); the
     * surrounding transaction then rolls back the reservations that did succeed.
     */
    private void reserveStock(Order order, Map<Sku, Product> productsBySku) {
        List<StockShortfall> shortfalls = new ArrayList<>();

        for (OrderItem item : order.getItems()) {
            Product product = productsBySku.get(item.getSku());
            int updated = productRepository.tryReserveStock(product.getId(), item.getQuantity());

            if (updated == 0) {
                shortfalls.add(
                        new StockShortfall(
                                item.getSku().value(),
                                item.getQuantity(),
                                product.getAvailableStock()));
            }
        }

        if (!shortfalls.isEmpty()) {
            throw new InsufficientStockException(shortfalls);
        }
    }

    private Map<Sku, Product> loadProductsBySku(CreateOrderRequest request) {
        Set<Sku> skus =
                request.items().stream()
                        .map(item -> new Sku(item.sku()))
                        .collect(Collectors.toSet());

        Map<Sku, Product> productsBySku = new HashMap<>();
        for (Product product : productRepository.findAllBySkuIn(skus)) {
            productsBySku.put(product.getSku(), product);
        }
        return productsBySku;
    }

    /**
     * Computes a stable SHA-256 fingerprint of the request's normalized items (each SKU canonical,
     * paired with its quantity, sorted so order is irrelevant). Two requests with the same lines in
     * any order produce the same fingerprint, which is what lets {@link #createOrder} tell a
     * genuine replay from an idempotency-key conflict.
     */
    String canonicalFingerprint(CreateOrderRequest request) {
        String canonical =
                request.items().stream()
                        .map(item -> new Sku(item.sku()).value() + ":" + item.quantity())
                        .sorted()
                        .collect(Collectors.joining(";"));

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is guaranteed present on every JVM; this can only mean a broken platform.
            throw new IllegalStateException("SHA-256 message digest is not available", exception);
        }
    }

    /**
     * Builds a validated, priced order — but does not yet reserve stock. Validation runs over every
     * line first and accumulates all problems (unknown / inactive / duplicate SKU) into a single
     * batched {@link OrderValidationException} (422) so the client can fix everything at once
     * (FR-004 / FR-005). Only once the request is fully valid are the line items created (with
     * price snapshots) and the running total accumulated.
     */
    Order buildReservedOrder(
            String idempotencyKey,
            String fingerprint,
            CreateOrderRequest request,
            Map<Sku, Product> productsBySku) {
        List<OrderValidationError> errors = new ArrayList<>();
        Set<Sku> duplicateSkuCheck = new HashSet<>();

        for (OrderItemRequest itemRequest : request.items()) {
            collectItemValidationErrors(itemRequest, productsBySku, errors, duplicateSkuCheck);
        }

        if (!errors.isEmpty()) {
            throw new OrderValidationException(errors);
        }

        Order newOrder = createNewOrder(idempotencyKey, fingerprint);
        for (OrderItemRequest itemRequest : request.items()) {
            processLineItem(itemRequest, newOrder, productsBySku);
        }

        return newOrder;
    }

    private void processLineItem(
            OrderItemRequest itemRequest, Order newOrder, Map<Sku, Product> productsBySku) {
        Sku sku = new Sku(itemRequest.sku());
        Product product = productsBySku.get(sku);

        OrderItem item = createOrderItem(product, itemRequest.quantity());
        newOrder.addItem(item);
        newOrder.setTotalNetInCents(newOrder.getTotalNetInCents().plus(item.getLineNetInCents()));
    }

    /**
     * Classifies a single line and appends a validation error if it fails. Duplicate SKUs are
     * detected first (via {@code duplicateSkuCheck}), then unknown SKUs, then inactive products —
     * at most one error per line. Appends to the shared {@code errors} list rather than throwing so
     * the caller can report all problems together.
     */
    private void collectItemValidationErrors(
            OrderItemRequest itemRequest,
            Map<Sku, Product> productsBySku,
            List<OrderValidationError> errors,
            Set<Sku> duplicateSkuCheck) {
        Sku sku = new Sku(itemRequest.sku());
        if (!(duplicateSkuCheck.add(sku))) {
            errors.add(new OrderValidationError(sku.value(), OrderValidationReason.DUPLICATE_SKU));
            return;
        }

        Product product = productsBySku.get(sku);
        if (product == null) {
            errors.add(new OrderValidationError(sku.value(), OrderValidationReason.UNKNOWN_SKU));
            return;
        }

        if (!product.isActive()) {
            errors.add(
                    new OrderValidationError(sku.value(), OrderValidationReason.INACTIVE_PRODUCT));
        }
    }

    /**
     * Builds a line item, copying the product's SKU and unit price into the item as snapshots. This
     * price snapshotting is what makes later product price changes leave existing orders unchanged
     * (FR-006).
     */
    private OrderItem createOrderItem(Product product, int quantity) {
        OrderItem orderItem = new OrderItem();

        orderItem.setUnitNetPriceInCents(product.getNetPriceInCents());
        orderItem.setQuantity(quantity);
        orderItem.setLineNetInCents(product.getNetPriceInCents().times(quantity));
        orderItem.setSku(product.getSku());

        return orderItem;
    }

    private Order createNewOrder(String idempotencyKey, String fingerprint) {
        Order newOrder = new Order();

        newOrder.setStatus(OrderStatus.RESERVED);
        newOrder.setCurrency(CURRENCY);
        newOrder.setCreatedAt(Instant.now(clock));
        newOrder.setIdempotencyKey(idempotencyKey);
        newOrder.setRequestFingerprint(fingerprint);
        newOrder.setTotalNetInCents(Money.zero());
        return newOrder;
    }
}
