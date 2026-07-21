package dev.patricklehmann.checkout_lab.exceptions.orders;

/** The reason a single order line failed validation (FR-004 / FR-005). */
public enum OrderValidationReason {
    UNKNOWN_SKU,
    INACTIVE_PRODUCT,
    DUPLICATE_SKU
}
