package dev.patricklehmann.checkout_lab.exceptions.orders;

import dev.patricklehmann.checkout_lab.entities.orders.OrderStatus;
import lombok.Getter;

@Getter
public class OrderTransitionException extends RuntimeException {
    private final String orderReference;
    private final OrderStatus existingStatus;
    private final OrderStatus attemptedResult;

    public OrderTransitionException(
            String orderReference, OrderStatus existingStatus, OrderStatus attemptedResult) {
        super(
                "Conflicting result for OrderTransition '%s': already %s, received %s"
                        .formatted(orderReference, existingStatus, attemptedResult));
        this.orderReference = orderReference;
        this.existingStatus = existingStatus;
        this.attemptedResult = attemptedResult;
    }
}
