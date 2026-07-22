package dev.patricklehmann.checkout_lab.exceptions.orders;

import dev.patricklehmann.checkout_lab.entities.orders.OrderStatus;
import lombok.Getter;

/**
 * Raised when a payment is started for an order whose status does not allow it (e.g. a cancelled
 * order). Mapped to HTTP 409. {@code OrderAlreadyPaidException} covers the already-paid case
 * specifically; this covers the other non-payable states.
 */
@Getter
public class OrderNotPayableException extends RuntimeException {
    private final long orderId;
    private final OrderStatus status;

    public OrderNotPayableException(long orderId, OrderStatus status) {
        super("Order '%d' cannot be paid in status %s".formatted(orderId, status));
        this.orderId = orderId;
        this.status = status;
    }
}
