package dev.patricklehmann.checkout_lab.entities.orders;

/**
 * The business lifecycle states of an order.
 *
 * <p>Only {@link #RESERVED} is reachable in Etappe B (order creation reserves stock). The remaining
 * states are persistable now but are driven by payment and cancellation in Etappe C.
 */
public enum OrderStatus {
    /** Stock has been reserved; payment has not yet completed successfully. */
    RESERVED,

    /** Payment completed successfully; the order is permanently paid. */
    PAID,

    /** A payment attempt failed or was declined. */
    PAYMENT_FAILED,

    /** The order was cancelled and its reserved stock released. */
    CANCELLED
}
