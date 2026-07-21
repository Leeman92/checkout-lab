package dev.patricklehmann.checkout_lab.entities.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import java.util.Objects;

/**
 * A product SKU as a value object.
 *
 * <p>The invariant — non-blank and always upper-cased — is enforced in the constructor, so a {@code
 * Sku} instance is <em>always</em> canonical. No code that receives a {@code Sku} ever needs to
 * defensively re-normalize or re-validate it; holding the type is proof of the invariant.
 */
public record Sku(String value) {

    public Sku {
        Objects.requireNonNull(value, "SKU must not be null");
        value = value.strip().toUpperCase(Locale.ROOT);
        if (value.isEmpty()) {
            throw new IllegalArgumentException("SKU must not be blank");
        }
    }

    /** Deserializes a {@code Sku} from a bare JSON string (e.g. {@code "tshirt-blk-m"}). */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static Sku of(String value) {
        return new Sku(value);
    }

    /** Serializes a {@code Sku} as its bare string value rather than a wrapping object. */
    @JsonValue
    @Override
    public String value() {
        return value;
    }
}
