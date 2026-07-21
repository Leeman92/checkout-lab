package dev.patricklehmann.checkout_lab.controller.api.orders.dto;

import dev.patricklehmann.checkout_lab.entities.orders.Order;
import dev.patricklehmann.checkout_lab.entities.orders.OrderStatus;
import java.time.Instant;
import java.util.List;

/**
 * API response view of a full order aggregate, including its line items. {@code totalNetInCents} is
 * exposed as bare cents to match the money wire contract.
 */
public record OrderResponse(
        Long id,
        OrderStatus status,
        String currency,
        long totalNetInCents,
        Instant createdAt,
        List<OrderItemResponse> items) {

    public OrderResponse {
        // Defensive, unmodifiable copy so the response record never aliases a mutable list.
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** Projects a persisted {@link Order} aggregate (and its items) onto its response view. */
    public static OrderResponse from(Order order) {
        List<OrderItemResponse> items =
                order.getItems().stream().map(OrderItemResponse::from).toList();

        return new OrderResponse(
                order.getId(),
                order.getStatus(),
                order.getCurrency(),
                order.getTotalNetInCents().amountInCents(),
                order.getCreatedAt(),
                items);
    }
}
