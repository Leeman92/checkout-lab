package dev.patricklehmann.checkout_lab.exceptions.orders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Carries <em>all</em> order lines that could not be reserved, so the client sees every stock
 * shortfall at once (FR-009 / FR-010). Thrown after attempting every line; the surrounding
 * transaction rolls back any reservations that did succeed, so no partial reservation remains.
 */
public class InsufficientStockException extends RuntimeException {

    private final List<StockShortfall> shortfalls;

    public InsufficientStockException(List<StockShortfall> shortfalls) {
        super("Insufficient stock for %d order line(s)".formatted(shortfalls.size()));
        this.shortfalls = new ArrayList<>(shortfalls);
    }

    public List<StockShortfall> getShortfalls() {
        return Collections.unmodifiableList(shortfalls);
    }
}
