package dev.patricklehmann.checkout_lab;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patricklehmann.checkout_lab.support.IntegrationTest;
import org.junit.jupiter.api.Test;

/** Confirms the Testcontainers integration harness boots: real PostgreSQL, Flyway V1–V4 applied. */
class IntegrationInfraTest extends IntegrationTest {

    @Test
    void schemaIsMigratedAndReachable() {
        Integer applied =
                jdbc.queryForObject(
                        "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);
        assertThat(applied).isEqualTo(5);

        Integer products = jdbc.queryForObject("SELECT count(*) FROM products", Integer.class);
        assertThat(products).isZero();
    }
}
