package dev.patricklehmann.checkout_lab.exceptions.orders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Carries <em>all</em> validation problems found in a single order request (unknown / inactive /
 * duplicate SKUs), so the client can fix everything at once rather than one error per resubmission
 * (FR-004 / FR-005).
 */
public class OrderValidationException extends RuntimeException {

    private final List<OrderValidationError> errors;

    public OrderValidationException(List<OrderValidationError> errors) {
        super("Order request has %d validation error(s)".formatted(errors.size()));
        this.errors = new ArrayList<>(errors);
    }

    public List<OrderValidationError> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
