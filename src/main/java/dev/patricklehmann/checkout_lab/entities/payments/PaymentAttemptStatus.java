package dev.patricklehmann.checkout_lab.entities.payments;

/**
 * The outcome of a single payment attempt.
 *
 * <p>{@link #PENDING} is non-terminal and may later resolve to {@link #SUCCESS} or {@link
 * #DECLINED} via a callback; the terminal states are immutable once reached.
 */
public enum PaymentAttemptStatus {
    /** The gateway has not yet decided; a later result will resolve it. */
    PENDING,

    /** The payment succeeded; the owning order is now permanently paid. */
    SUCCESS,

    /** The payment was declined; the order stays reserved so another attempt can be made. */
    DECLINED
}
