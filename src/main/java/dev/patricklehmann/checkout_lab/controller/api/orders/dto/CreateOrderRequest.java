package dev.patricklehmann.checkout_lab.controller.api.orders.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * Request body for creating an order. The idempotency key is transported via the {@code
 * Idempotency-Key} header, not this body, so it never affects the request fingerprint.
 */
public record CreateOrderRequest(@NotEmpty List<@Valid OrderItemRequest> items) {
    public CreateOrderRequest {
        // Defensive, unmodifiable copy: the record must not alias a list the caller can still
        // mutate.
        // A null (missing "items") becomes empty so @NotEmpty reports it as a clean validation
        // error.
        items = items == null ? List.of() : List.copyOf(items);
    }
}
