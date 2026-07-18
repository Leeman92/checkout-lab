package dev.patricklehmann.checkout_lab;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.patricklehmann.checkout_lab.controller.CustomErrorController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CustomErrorController.class)
class CustomErrorControllerTests {

    @Autowired private MockMvc mockMvc;

    @Test
    void shouldReturnInternalServerError() throws Exception {
        mockMvc.perform(get("/error")).andExpect(status().is5xxServerError());
    }
}
