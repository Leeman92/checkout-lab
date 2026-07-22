package dev.patricklehmann.checkout_lab;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.patricklehmann.checkout_lab.controller.api.ApiResponseMetadataAdvice;
import dev.patricklehmann.checkout_lab.exceptions.GlobalExceptionHandler;
import dev.patricklehmann.checkout_lab.filters.RequestIdFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Drives the RFC 7807 mapping of persistence-level concurrency conflicts. When two requests race
 * and the database rejects the loser — a unique-constraint violation ({@link
 * DataIntegrityViolationException}) or a stale {@code @Version} ({@link
 * ObjectOptimisticLockingFailureException}) — the client must get a clean {@code 409
 * urn:problem:concurrent-modification}, not a leaked 500. Until the handler exists these are red.
 */
class ConcurrencyConflictMappingTests {

    private MockMvc mockMvc;

    @BeforeEach
    void setUpMvc() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(new BoomController())
                        .setControllerAdvice(
                                new ApiResponseMetadataAdvice(), new GlobalExceptionHandler())
                        .addFilters(new RequestIdFilter())
                        .build();
    }

    @Test
    void mapsUniqueConstraintViolationTo409() throws Exception {
        mockMvc.perform(get("/boom/integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:problem:concurrent-modification"))
                // The client-facing detail must never carry the raw DB message (ER-003): asserting
                // the constraint name is absent fails loudly if someone reintroduces getMessage().
                .andExpect(jsonPath("$.detail", not(containsString("constraint"))));
    }

    @Test
    void mapsOptimisticLockFailureTo409() throws Exception {
        mockMvc.perform(get("/boom/optimistic"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:problem:concurrent-modification"));
    }

    @RestController
    static final class BoomController {

        @GetMapping("/boom/integrity")
        String integrity() {
            throw new DataIntegrityViolationException(
                    "duplicate key value violates unique constraint");
        }

        @GetMapping("/boom/optimistic")
        String optimistic() {
            throw new ObjectOptimisticLockingFailureException("Order", 1L);
        }
    }
}
