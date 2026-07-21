package dev.patricklehmann.checkout_lab.entities.shared;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.jspecify.annotations.Nullable;

/**
 * Maps the {@link Sku} value object to a plain {@code varchar} column and back. {@code autoApply}
 * means every {@code Sku} attribute on every entity is converted automatically, with no per-field
 * annotation required.
 */
@Converter(autoApply = true)
public class SkuConverter implements AttributeConverter<Sku, String> {

    @Override
    public @Nullable String convertToDatabaseColumn(@Nullable Sku sku) {
        return sku == null ? null : sku.value();
    }

    @Override
    public @Nullable Sku convertToEntityAttribute(@Nullable String dbData) {
        return dbData == null ? null : Sku.of(dbData);
    }
}
