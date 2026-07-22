package dev.patricklehmann.checkout_lab;

import static dev.patricklehmann.checkout_lab.support.TestEntities.NOW;
import static dev.patricklehmann.checkout_lab.support.TestEntities.orderStateMachine;
import static dev.patricklehmann.checkout_lab.support.TestEntities.paymentStateMachine;
import static dev.patricklehmann.checkout_lab.support.TestEntities.reservedOrder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patricklehmann.checkout_lab.entities.orders.Order;
import dev.patricklehmann.checkout_lab.entities.orders.OrderStatus;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttempt;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptStatus;
import dev.patricklehmann.checkout_lab.entities.shared.Money;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderAlreadyPaidException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderNotFoundException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderNotPayableException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderTransitionException;
import dev.patricklehmann.checkout_lab.exceptions.payments.PaymentAttemptNotFoundException;
import dev.patricklehmann.checkout_lab.exceptions.payments.PaymentConflictException;
import dev.patricklehmann.checkout_lab.exceptions.payments.PaymentInProgressException;
import dev.patricklehmann.checkout_lab.services.payments.PaymentService;
import dev.patricklehmann.checkout_lab.support.FakeOrderRepository;
import dev.patricklehmann.checkout_lab.support.FakePaymentAttemptRepository;
import dev.patricklehmann.checkout_lab.support.FakePaymentResultRepository;
import dev.patricklehmann.checkout_lab.support.ScriptedPaymentGateway;
import java.time.Clock;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * Fast, deterministic unit tests for the payment lifecycle. They drive the real {@link
 * PaymentService} against in-memory fakes, a fixed {@link Clock}, a {@link ScriptedPaymentGateway}
 * (predictable {@code gw-N} references), and the same transition tables the production beans use —
 * so the state-machine logic in {@code startPayment} / {@code resolvePayment} is genuinely
 * exercised, not stubbed.
 */
class PaymentServiceTests {

    private final FakeOrderRepository orders = new FakeOrderRepository();
    private final FakePaymentAttemptRepository attempts = new FakePaymentAttemptRepository();
    private final FakePaymentResultRepository results = new FakePaymentResultRepository();
    private final ScriptedPaymentGateway gateway = new ScriptedPaymentGateway();
    private final PaymentService service =
            new PaymentService(
                    orders,
                    attempts,
                    results,
                    gateway,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    paymentStateMachine(),
                    orderStateMachine());

    private Order givenReservedOrder() {
        Order order = reservedOrder(1L, "TSHIRT-BLK-M", 2);
        orders.byId.put(1L, order);
        return order;
    }

    // --- startPayment: immediate outcomes -----------------------------------------------------

    @Test
    void successPaysOrderAndLogsResult() {
        Order order = givenReservedOrder();

        PaymentAttempt attempt = service.startPayment(1L, PaymentAttemptStatus.SUCCESS);

        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
        assertThat(attempt.getAttemptNumber()).isEqualTo(1);
        assertThat(attempt.getGatewayReference()).isEqualTo("gw-1");
        assertThat(attempt.getAmountInCents()).isEqualTo(Money.ofCents(3998));
        assertThat(attempt.getResolvedAt()).isEqualTo(NOW);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(order.getUpdatedAt()).isEqualTo(NOW);

        assertThat(results.saved)
                .singleElement()
                .satisfies(
                        row -> {
                            assertThat(row.getResult()).isEqualTo(PaymentAttemptStatus.SUCCESS);
                            assertThat(row.getMessageId()).isEqualTo("gw-1#0");
                        });
    }

    @Test
    void declineLeavesOrderReservedForRetry() {
        Order order = givenReservedOrder();

        PaymentAttempt attempt = service.startPayment(1L, PaymentAttemptStatus.DECLINED);

        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.DECLINED);
        assertThat(attempt.getResolvedAt()).isEqualTo(NOW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RESERVED);
        assertThat(results.saved).hasSize(1);
    }

    @Test
    void pendingLeavesAttemptOpenWithoutResult() {
        Order order = givenReservedOrder();

        PaymentAttempt attempt = service.startPayment(1L, PaymentAttemptStatus.PENDING);

        assertThat(attempt.getStatus()).isEqualTo(PaymentAttemptStatus.PENDING);
        assertThat(attempt.getResolvedAt()).isNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RESERVED);
        assertThat(results.saved).isEmpty();
    }

    @Test
    void retryAfterDeclineSucceedsAsSecondAttempt() {
        Order order = givenReservedOrder();

        service.startPayment(1L, PaymentAttemptStatus.DECLINED);
        PaymentAttempt second = service.startPayment(1L, PaymentAttemptStatus.SUCCESS);

        assertThat(second.getAttemptNumber()).isEqualTo(2);
        assertThat(second.getGatewayReference()).isEqualTo("gw-2");
        assertThat(second.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(attempts.saved).hasSize(2);
        assertThat(attempts.saved)
                .filteredOn(a -> a.getStatus() == PaymentAttemptStatus.SUCCESS)
                .hasSize(1);
    }

    // --- startPayment: guards -----------------------------------------------------------------

    @Test
    void startRejectedWhenOrderAlreadyPaid() {
        Order order = givenReservedOrder();
        order.setStatus(OrderStatus.PAID);

        assertThatThrownBy(() -> service.startPayment(1L, PaymentAttemptStatus.SUCCESS))
                .isInstanceOf(OrderAlreadyPaidException.class);
    }

    @Test
    void startRejectedWhenSuccessfulAttemptExists() {
        Order order = givenReservedOrder();
        attempts.save(terminalAttempt(order, PaymentAttemptStatus.SUCCESS, "gw-prior", 1));

        assertThatThrownBy(() -> service.startPayment(1L, PaymentAttemptStatus.SUCCESS))
                .isInstanceOf(OrderAlreadyPaidException.class);
    }

    @Test
    void startRejectedWhenOrderCancelled() {
        Order order = givenReservedOrder();
        order.setStatus(OrderStatus.CANCELLED);

        assertThatThrownBy(() -> service.startPayment(1L, PaymentAttemptStatus.SUCCESS))
                .isInstanceOf(OrderNotPayableException.class);
    }

    @Test
    void startRejectedWhilePendingAttemptInFlight() {
        givenReservedOrder();
        service.startPayment(1L, PaymentAttemptStatus.PENDING);

        assertThatThrownBy(() -> service.startPayment(1L, PaymentAttemptStatus.SUCCESS))
                .isInstanceOf(PaymentInProgressException.class);
    }

    @Test
    void startRejectedWhenOrderMissing() {
        assertThatThrownBy(() -> service.startPayment(999L, PaymentAttemptStatus.SUCCESS))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // --- resolvePayment: callbacks, dedup, conflicts ------------------------------------------

    @Test
    void callbackSuccessResolvesPendingAttempt() {
        Order order = givenReservedOrder();
        service.startPayment(1L, PaymentAttemptStatus.PENDING);

        PaymentAttempt resolved =
                service.resolvePayment("gw-1", PaymentAttemptStatus.SUCCESS, "msg-1");

        assertThat(resolved.getStatus()).isEqualTo(PaymentAttemptStatus.SUCCESS);
        assertThat(resolved.getResolvedAt()).isEqualTo(NOW);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(results.existsByMessageId("msg-1")).isTrue();
    }

    @Test
    void duplicateMessageIsIgnored() {
        Order order = givenReservedOrder();
        service.startPayment(1L, PaymentAttemptStatus.PENDING);

        service.resolvePayment("gw-1", PaymentAttemptStatus.SUCCESS, "msg-1");
        service.resolvePayment("gw-1", PaymentAttemptStatus.SUCCESS, "msg-1");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(results.saved).hasSize(1);
    }

    @Test
    void conflictingResultIsRejectedButLoggedForAudit() {
        Order order = givenReservedOrder();
        service.startPayment(1L, PaymentAttemptStatus.PENDING);
        service.resolvePayment("gw-1", PaymentAttemptStatus.SUCCESS, "msg-1");

        assertThatThrownBy(
                        () ->
                                service.resolvePayment(
                                        "gw-1", PaymentAttemptStatus.DECLINED, "msg-2"))
                .isInstanceOf(PaymentConflictException.class)
                .satisfies(
                        thrown -> {
                            PaymentConflictException conflict = (PaymentConflictException) thrown;
                            assertThat(conflict.getGatewayReference()).isEqualTo("gw-1");
                            assertThat(conflict.getExistingStatus())
                                    .isEqualTo(PaymentAttemptStatus.SUCCESS);
                            assertThat(conflict.getAttemptedResult())
                                    .isEqualTo(PaymentAttemptStatus.DECLINED);
                        });

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(attempts.saved)
                .singleElement()
                .extracting(PaymentAttempt::getStatus)
                .isEqualTo(PaymentAttemptStatus.SUCCESS);
        assertThat(results.saved).hasSize(2);
    }

    @Test
    void unknownGatewayReferenceThrows() {
        givenReservedOrder();

        assertThatThrownBy(
                        () ->
                                service.resolvePayment(
                                        "gw-unknown", PaymentAttemptStatus.SUCCESS, "msg-1"))
                .isInstanceOf(PaymentAttemptNotFoundException.class)
                .extracting("gatewayReference")
                .isEqualTo("gw-unknown");
    }

    @Test
    void successAfterCancelConflictsAndKeepsOrderCancelled() {
        Order order = givenReservedOrder();
        service.startPayment(1L, PaymentAttemptStatus.PENDING);
        order.setStatus(OrderStatus.CANCELLED);

        assertThatThrownBy(
                        () -> service.resolvePayment("gw-1", PaymentAttemptStatus.SUCCESS, "msg-1"))
                .isInstanceOf(OrderTransitionException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(results.existsByMessageId("msg-1")).isTrue();
    }

    private static PaymentAttempt terminalAttempt(
            Order order, PaymentAttemptStatus status, String gatewayReference, int attemptNumber) {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setOrder(order);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setStatus(status);
        attempt.setGatewayReference(gatewayReference);
        attempt.setAmountInCents(order.getTotalNetInCents());
        attempt.setCreatedAt(NOW);
        attempt.setResolvedAt(NOW);
        return attempt;
    }
}
