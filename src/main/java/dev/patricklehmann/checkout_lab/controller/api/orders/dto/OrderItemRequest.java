package dev.patricklehmann.checkout_lab.controller.api.orders.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * A requested order position.
 *
 * <p>{@code quantity} is a boxed {@link Integer} so that a missing value fails {@link NotNull}
 * validation rather than silently defaulting to {@code 0}.
 */
public record OrderItemRequest(@NotBlank String sku, @NotNull @Positive Integer quantity) {}
