package dev.patricklehmann.checkout_lab.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests that need a <em>real</em> PostgreSQL — the concurrency, rollback
 * and persistence behavior that in-memory fakes cannot prove (Etappe D).
 *
 * <p>Uses the Testcontainers <b>singleton-container</b> pattern: one container is started once (in
 * a static initializer) and shared by every subclass for the whole test-run — far cheaper than a
 * container per class. It is never explicitly stopped; Testcontainers' Ryuk sidecar reaps it when
 * the JVM exits. {@link DynamicPropertySource} points Spring's datasource (and therefore Flyway,
 * which runs the real V1–V4 migrations on startup) at the container, so nothing depends on host
 * environment variables.
 *
 * <p>Because these tests commit across multiple threads/transactions, the usual per-test
 * transactional rollback is unavailable; instead {@link #resetDatabase()} truncates the domain
 * tables before each test so every test starts from a known-empty schema.
 */
@SpringBootTest
public abstract class IntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18.4");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired protected JdbcTemplate jdbc;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute(
                "TRUNCATE payment_results, payment_attempts, order_items, orders, products"
                        + " RESTART IDENTITY CASCADE");
    }
}
