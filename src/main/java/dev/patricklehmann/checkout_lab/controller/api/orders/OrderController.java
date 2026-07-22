package dev.patricklehmann.checkout_lab.controller.api.orders;

import dev.patricklehmann.checkout_lab.controller.api.orders.dto.CreateOrderRequest;
import dev.patricklehmann.checkout_lab.controller.api.orders.dto.OrderResponse;
import dev.patricklehmann.checkout_lab.entities.orders.Order;
import dev.patricklehmann.checkout_lab.services.orders.OrderCreationResult;
import dev.patricklehmann.checkout_lab.services.orders.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for creating and reading orders. Thin transport layer: it delegates all business
 * logic to {@link OrderService} and only translates the result into the appropriate HTTP status.
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * Creates an order (or replays an existing one). The {@code Idempotency-Key} header — not the
     * body — keys the idempotency check, so identical payloads under one key never create
     * duplicates. Returns 201 for a fresh order and 200 for an idempotent replay (FR-011).
     */
    @PostMapping({"", "/"})
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderCreationResult result = orderService.createOrder(idempotencyKey, request);

        // A fresh order is 201 Created; a replay of an existing idempotency key is 200 OK, since
        // nothing new was created (FR-011).
        HttpStatus status = result.replay() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(OrderResponse.from(result.order()));
    }

    @GetMapping({"/{id}", "/{id}/"})
    public OrderResponse getOrder(@PathVariable long id) {
        Order order = orderService.getOrder(id);
        return OrderResponse.from(order, orderService.getPaymentAttempts(order));
    }

    @PostMapping({"/{id}/cancel", "/{id}/cancel/"})
    public OrderResponse cancelOrder(@PathVariable long id) {
        Order order = orderService.cancelOrder(id);
        return OrderResponse.from(order, orderService.getPaymentAttempts(order));
    }
}
