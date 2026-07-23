package dev.patricklehmann.checkout_lab;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.patricklehmann.checkout_lab.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the Actuator health endpoint (NFR-002): the application reports that it is up and that
 * it can actually reach PostgreSQL. Runs against the real Testcontainers database, so the {@code
 * db} health component reflects a genuine connection, not a mock.
 */
@AutoConfigureMockMvc
class HealthEndpointTests extends IntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void healthReportsUpWithDatabaseConnectivity() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                .andExpect(jsonPath("$.components.diskSpace.status").value("UP"))
                .andExpect(jsonPath("$.components.livenessState.status").value("UP"))
                .andExpect(jsonPath("$.components.ping.status").value("UP"))
                .andExpect(jsonPath("$.components.readinessState.status").value("UP"))
                .andExpect(jsonPath("$.components.ssl.status").value("UP"))
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
