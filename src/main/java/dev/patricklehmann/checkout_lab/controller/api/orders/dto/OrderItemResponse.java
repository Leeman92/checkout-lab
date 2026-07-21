package dev.patricklehmann.checkout_lab.controller.api.orders.dto;

import dev.patricklehmann.checkout_lab.entities.orders.OrderItem;

/**
 * API response view of a single order line. Prices are exposed as bare cents (matching the {@link
 * dev.patricklehmann.checkout_lab.entities.shared.Money} wire contract) and reflect the snapshot
 * captured at order time, not the live product price (FR-006).
 */
public record OrderItemResponse(
        String sku, int quantity, long unitNetPriceInCents, long lineNetInCents) {

    /** Projects a persisted {@link OrderItem} entity onto its response representation. */
    public static OrderItemResponse from(OrderItem item) {
        return new OrderItemResponse(
                item.getSku().value(),
                item.getQuantity(),
                item.getUnitNetPriceInCents().amountInCents(),
                item.getLineNetInCents().amountInCents());
    }
}
