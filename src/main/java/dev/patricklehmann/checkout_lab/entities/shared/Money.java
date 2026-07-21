package dev.patricklehmann.checkout_lab.entities.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * A monetary amount in EUR, stored as an exact number of cents.
 *
 * <p>Money is a value object: it owns the invariant (never negative), the overflow-safe arithmetic,
 * and its own formatting. Using integer cents means every {@link #plus} and {@link #times} is exact
 * — there is no floating point and therefore no rounding error (NFR-009). Currency is implicit (the
 * project is EUR-only, FR-007); introducing multi-currency later would add a currency field and
 * make {@code plus} reject mismatched currencies.
 */
public record Money(long amountInCents) {

    public Money {
        if (amountInCents < 0) {
            throw new IllegalArgumentException("Money must not be negative: " + amountInCents);
        }
    }

    /** Deserializes from a bare JSON number of cents (e.g. {@code 1999}). */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Money ofCents(long amountInCents) {
        return new Money(amountInCents);
    }

    public static Money zero() {
        return new Money(0);
    }

    /** Serializes as a bare cents number, preserving the existing API contract. */
    @JsonValue
    @Override
    public long amountInCents() {
        return amountInCents;
    }

    /** Adds two amounts, failing loudly on overflow rather than silently wrapping. */
    public Money plus(Money other) {
        return new Money(Math.addExact(this.amountInCents, other.amountInCents));
    }

    /** Multiplies the amount by a whole quantity, failing loudly on overflow. */
    public Money times(int factor) {
        return new Money(Math.multiplyExact(this.amountInCents, factor));
    }

    /**
     * Renders this amount as a plain EUR decimal string (e.g. {@code 1999} → {@code "19.99"}).
     *
     * <p>Implemented as a learning challenge — see the TODO in the method body.
     */
    public String formatted() {
        return String.format("%d.%02d", amountInCents / 100, amountInCents % 100);
    }
}
