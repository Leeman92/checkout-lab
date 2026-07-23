package dev.patricklehmann.checkout_lab.entities.orders;

/**
 * The business lifecycle states of an order. An order is created {@link #RESERVED}; from there a
 * successful payment moves it to {@link #PAID} and a cancellation moves it to {@link #CANCELLED}.
 * Both are terminal. A declined payment is <em>not</em> a distinct state: the order stays {@link
 * #RESERVED} so it can be retried, which is why there is no {@code PAYMENT_FAILED} state.
 */
public enum OrderStatus {
    /** Stock has been reserved; payment has not yet completed successfully. */
    RESERVED,

    /** Payment completed successfully; the order is permanently paid. */
    PAID,

    /** The order was cancelled and its reserved stock released. */
    CANCELLED
}
