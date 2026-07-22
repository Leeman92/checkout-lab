package dev.patricklehmann.checkout_lab.controller.api.orders.dto;

import dev.patricklehmann.checkout_lab.entities.orders.Order;
import dev.patricklehmann.checkout_lab.entities.orders.OrderStatus;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttempt;
import java.time.Instant;
import java.util.List;

/**
 * API response view of a full order aggregate: its line items and its known payment attempts
 * (FR-013). {@code totalNetInCents} is exposed as bare cents to match the money wire contract.
 */
public record OrderResponse(
        Long id,
        OrderStatus status,
        String currency,
        long totalNetInCents,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemResponse> items,
        List<OrderPaymentView> payments) {

    public OrderResponse {
        // Defensive, unmodifiable copies so the response record never aliases mutable lists.
        items = items == null ? List.of() : List.copyOf(items);
        payments = payments == null ? List.of() : List.copyOf(payments);
    }

    /** Projects an order with no payment attempts (e.g. a freshly created order). */
    public static OrderResponse from(Order order) {
        return from(order, List.of());
    }

    /** Projects a persisted {@link Order} together with its payment attempts (FR-013). */
    public static OrderResponse from(Order order, List<PaymentAttempt> attempts) {
        List<OrderItemResponse> items =
                order.getItems().stream().map(OrderItemResponse::from).toList();
        List<OrderPaymentView> payments = attempts.stream().map(OrderPaymentView::from).toList();

        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getCurrency(),
                order.getTotalNetInCents().amountInCents(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                items,
                payments);
    }
}
