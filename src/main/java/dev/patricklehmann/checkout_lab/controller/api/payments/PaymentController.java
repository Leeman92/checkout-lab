package dev.patricklehmann.checkout_lab.controller.api.payments;

import dev.patricklehmann.checkout_lab.controller.api.payments.dto.PaymentAttemptResponse;
import dev.patricklehmann.checkout_lab.controller.api.payments.dto.PaymentCallbackRequest;
import dev.patricklehmann.checkout_lab.controller.api.payments.dto.StartPaymentRequest;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttempt;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptStatus;
import dev.patricklehmann.checkout_lab.services.payments.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for payments: starting an attempt on an order, and receiving a (possibly delayed)
 * gateway result. A thin transport layer over {@link PaymentService}.
 */
@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Starts a payment attempt for an order. The body is optional; its {@code desiredOutcome}
     * steers the simulated gateway (default success, or PENDING to exercise a delayed callback).
     */
    @PostMapping({"/orders/{orderId}/payments", "/orders/{orderId}/payments/"})
    public ResponseEntity<PaymentAttemptResponse> startPayment(
            @PathVariable long orderId,
            @RequestBody(required = false) StartPaymentRequest request) {
        PaymentAttemptStatus desiredOutcome = request == null ? null : request.desiredOutcome();
        PaymentAttempt attempt = paymentService.startPayment(orderId, desiredOutcome);
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentAttemptResponse.from(attempt));
    }

    /**
     * Applies a gateway result message to an existing attempt (FR-020). Duplicate messages are
     * no-ops and conflicting results are rejected — see {@link PaymentService#resolvePayment}.
     */
    @PostMapping({"/payments/callback", "/payments/callback/"})
    public PaymentAttemptResponse resolvePayment(
            @Valid @RequestBody PaymentCallbackRequest request) {
        PaymentAttempt attempt =
                paymentService.resolvePayment(
                        request.gatewayReference(), request.result(), request.messageId());
        return PaymentAttemptResponse.from(attempt);
    }
}
