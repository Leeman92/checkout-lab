package dev.patricklehmann.checkout_lab;

import dev.patricklehmann.checkout_lab.entities.orders.OrderStatus;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptStatus;
import dev.patricklehmann.checkout_lab.entities.shared.StateMachine;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderTransitionException;
import dev.patricklehmann.checkout_lab.exceptions.payments.PaymentConflictException;
import java.time.Clock;
import java.util.EnumMap;
import java.util.EnumSet;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Spring Boot entry point for the checkout lab. Boots the application context and contributes the
 * shared {@link Clock} bean used by time-dependent logic.
 */
@SpringBootApplication
public class CheckoutLabApplication {

    static void main(String[] args) {
        SpringApplication.run(CheckoutLabApplication.class, args);
    }

    /**
     * A single injectable clock so time-dependent logic (e.g. an order's creation timestamp) can be
     * driven by a fixed clock in tests, keeping them deterministic (NFR-008).
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    StateMachine<PaymentAttemptStatus> paymentStateMachine() {
        EnumMap<PaymentAttemptStatus, EnumSet<PaymentAttemptStatus>> validTransitions =
                new EnumMap<>(PaymentAttemptStatus.class);

        validTransitions.put(
                PaymentAttemptStatus.PENDING,
                EnumSet.of(PaymentAttemptStatus.DECLINED, PaymentAttemptStatus.SUCCESS));
        validTransitions.put(
                PaymentAttemptStatus.DECLINED, EnumSet.noneOf(PaymentAttemptStatus.class));
        validTransitions.put(
                PaymentAttemptStatus.SUCCESS, EnumSet.noneOf(PaymentAttemptStatus.class));

        return new StateMachine<>(validTransitions, PaymentConflictException::new);
    }

    @Bean
    StateMachine<OrderStatus> orderStateMachine() {
        EnumMap<OrderStatus, EnumSet<OrderStatus>> validTransitions =
                new EnumMap<>(OrderStatus.class);

        validTransitions.put(OrderStatus.PAID, EnumSet.noneOf(OrderStatus.class));
        validTransitions.put(OrderStatus.PAYMENT_FAILED, EnumSet.noneOf(OrderStatus.class));
        validTransitions.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
        validTransitions.put(
                OrderStatus.RESERVED, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED));

        return new StateMachine<>(validTransitions, OrderTransitionException::new);
    }
}
