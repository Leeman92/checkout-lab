package dev.patricklehmann.checkout_lab.services.orders;

import dev.patricklehmann.checkout_lab.entities.orders.Order;

/**
 * The outcome of a create-order call.
 *
 * @param order the created (or previously created) order
 * @param replay {@code true} if this call matched an existing idempotency key and returned the
 *     already-stored order without creating a new one (FR-011); {@code false} for a fresh order
 */
public record OrderCreationResult(Order order, boolean replay) {}
