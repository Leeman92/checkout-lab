package dev.patricklehmann.checkout_lab.services.payments;

import dev.patricklehmann.checkout_lab.entities.orders.Order;
import dev.patricklehmann.checkout_lab.entities.orders.OrderRepository;
import dev.patricklehmann.checkout_lab.entities.orders.OrderStatus;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttempt;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptRepository;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptStatus;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentResult;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentResultRepository;
import dev.patricklehmann.checkout_lab.entities.shared.StateMachine;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderAlreadyPaidException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderNotFoundException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderNotPayableException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderTransitionException;
import dev.patricklehmann.checkout_lab.exceptions.payments.PaymentAttemptNotFoundException;
import dev.patricklehmann.checkout_lab.exceptions.payments.PaymentConflictException;
import dev.patricklehmann.checkout_lab.exceptions.payments.PaymentInProgressException;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Payment use cases: starting an attempt against a reserved order and resolving a (possibly
 * delayed) result. Every inbound result is written to the append-only {@link PaymentResult} log —
 * whose unique {@code messageId} makes redelivery a no-op — before it is applied to the attempt.
 */
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final OrderRepository orderRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final PaymentResultRepository paymentResultRepository;
    private final PaymentGateway gateway;
    private final Clock clock;
    private final StateMachine<PaymentAttemptStatus> paymentStateMachine;
    private final StateMachine<OrderStatus> orderStateMachine;

    /**
     * Starts a payment attempt for a reserved order and applies the gateway's immediate outcome. A
     * PENDING outcome leaves the attempt open for a later callback; a terminal outcome resolves it
     * at once.
     *
     * @throws OrderNotFoundException if the order does not exist
     * @throws OrderAlreadyPaidException if the order (or an existing attempt) is already paid
     * @throws OrderNotPayableException if the order is in a non-payable status (e.g. cancelled)
     * @throws PaymentInProgressException if a PENDING attempt for the order is still open (FR-024)
     */
    @Transactional
    public PaymentAttempt startPayment(long orderId, PaymentAttemptStatus desiredOutcome) {
        Order order =
                orderRepository
                        .findById(orderId)
                        .orElseThrow(() -> new OrderNotFoundException(orderId));

        OrderStatus status = order.getStatus();
        if (status == OrderStatus.PAID
                || paymentAttemptRepository.existsByOrderAndStatus(
                        order, PaymentAttemptStatus.SUCCESS)) {
            throw new OrderAlreadyPaidException(orderId);
        }
        if (status != OrderStatus.RESERVED) {
            throw new OrderNotPayableException(orderId, status);
        }
        if (paymentAttemptRepository.existsByOrderAndStatus(order, PaymentAttemptStatus.PENDING)) {
            throw new PaymentInProgressException(orderId);
        }

        PaymentAuthorization authorization = gateway.authorize(order, desiredOutcome);

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setOrder(order);
        attempt.setAttemptNumber(nextAttemptNumber(order));
        attempt.setStatus(PaymentAttemptStatus.PENDING);
        attempt.setGatewayReference(authorization.gatewayReference());
        attempt.setAmountInCents(order.getTotalNetInCents());
        attempt.setCreatedAt(Instant.now(clock));
        paymentAttemptRepository.save(attempt);

        if (authorization.status() != PaymentAttemptStatus.PENDING) {
            appendResult(attempt, authorization.status(), attempt.getGatewayReference() + "#0");
            recordAndApply(attempt, authorization.status());
        }

        return attempt;
    }

    /**
     * Applies a later result message (a gateway callback) to an existing attempt, identified by its
     * {@code gatewayReference}. Redelivery of the same {@code messageId} is a no-op (FR-021).
     *
     * <p>{@code noRollbackFor} the conflict cases so that the appended result row is committed for
     * traceability (FR-022) even though the client still receives a 409 — the append-only log is
     * the record of every inbound message, including conflicting ones.
     *
     * @throws PaymentAttemptNotFoundException if no attempt has that gateway reference (FR-020)
     * @throws PaymentConflictException if the result contradicts a settled attempt (FR-022)
     * @throws OrderTransitionException if a success arrives for a non-payable order (FR-029)
     */
    @Transactional(noRollbackFor = {PaymentConflictException.class, OrderTransitionException.class})
    public PaymentAttempt resolvePayment(
            String gatewayReference, PaymentAttemptStatus result, String messageId) {
        PaymentAttempt attempt =
                paymentAttemptRepository
                        .findByGatewayReference(gatewayReference)
                        .orElseThrow(() -> new PaymentAttemptNotFoundException(gatewayReference));

        if (paymentResultRepository.existsByMessageId(messageId)) {
            return attempt;
        }

        appendResult(attempt, result, messageId);
        recordAndApply(attempt, result);
        return attempt;
    }

    /**
     * Applies a terminal {@code result} (SUCCESS or DECLINED) to the attempt and, where
     * appropriate, to its order.
     */
    private void recordAndApply(PaymentAttempt attempt, PaymentAttemptStatus result) {
        Order order = attempt.getOrder();
        PaymentAttemptStatus newState =
                paymentStateMachine.requireValidTransition(
                        attempt.getStatus(), result, attempt.getGatewayReference());
        attempt.setResolvedAt(Instant.now(clock));
        attempt.setStatus(newState);

        if (newState != PaymentAttemptStatus.SUCCESS) {
            return;
        }

        OrderStatus newOrderStatus =
                orderStateMachine.requireValidTransition(
                        order.getStatus(), OrderStatus.PAID, order.getIdempotencyKey());
        order.setStatus(newOrderStatus);
        order.setUpdatedAt(Instant.now(clock));
    }

    private int nextAttemptNumber(Order order) {
        return paymentAttemptRepository
                .findTopByOrderOrderByAttemptNumberDesc(order)
                .map(attempt -> attempt.getAttemptNumber() + 1)
                .orElse(1);
    }

    private void appendResult(
            PaymentAttempt attempt, PaymentAttemptStatus result, String messageId) {
        PaymentResult row = new PaymentResult();
        row.setAttempt(attempt);
        row.setMessageId(messageId);
        row.setResult(result);
        row.setReceivedAt(Instant.now(clock));
        paymentResultRepository.save(row);
    }
}
