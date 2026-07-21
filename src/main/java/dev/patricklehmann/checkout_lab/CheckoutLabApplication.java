package dev.patricklehmann.checkout_lab;

import java.time.Clock;
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
}
