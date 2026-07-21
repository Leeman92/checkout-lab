package dev.patricklehmann.checkout_lab.entities.shared;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

/**
 * Maps the {@link Money} value object to a {@code bigint} column of cents and back. {@code
 * autoApply} converts every {@code Money} attribute on every entity with no per-field annotation.
 */
@Converter(autoApply = true)
public class MoneyConverter implements AttributeConverter<Money, Long> {

    @Override
    public @Nullable Long convertToDatabaseColumn(@Nullable Money money) {
        return money == null ? null : money.amountInCents();
    }

    @Override
    public @Nullable Money convertToEntityAttribute(@Nullable Long dbData) {
        return dbData == null ? null : Money.ofCents(dbData);
    }
}
