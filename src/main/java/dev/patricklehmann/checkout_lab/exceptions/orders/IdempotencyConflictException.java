package dev.patricklehmann.checkout_lab.exceptions.orders;

import lombok.Getter;

/**
 * Raised when the same idempotency key is reused for a request whose content differs from the
 * original (FR-012). The existing order is left untouched.
 */
@Getter
public class IdempotencyConflictException extends RuntimeException {
    private final String idempotencyKey;

    public IdempotencyConflictException(String idempotencyKey) {
        super(
                "Idempotency key '%s' was already used for a different request"
                        .formatted(idempotencyKey));
        this.idempotencyKey = idempotencyKey;
    }
}
