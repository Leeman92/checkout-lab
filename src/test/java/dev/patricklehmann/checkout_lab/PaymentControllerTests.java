package dev.patricklehmann.checkout_lab;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.patricklehmann.checkout_lab.controller.api.ApiResponseMetadataAdvice;
import dev.patricklehmann.checkout_lab.controller.api.payments.PaymentController;
import dev.patricklehmann.checkout_lab.entities.orders.Order;
import dev.patricklehmann.checkout_lab.entities.orders.OrderStatus;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttempt;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptStatus;
import dev.patricklehmann.checkout_lab.entities.shared.Money;
import dev.patricklehmann.checkout_lab.exceptions.GlobalExceptionHandler;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderAlreadyPaidException;
import dev.patricklehmann.checkout_lab.exceptions.payments.PaymentAttemptNotFoundException;
import dev.patricklehmann.checkout_lab.exceptions.payments.PaymentConflictException;
import dev.patricklehmann.checkout_lab.filters.RequestIdFilter;
import dev.patricklehmann.checkout_lab.services.payments.PaymentService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PaymentControllerTests {

    private MockMvc mockMvc;
    private StubPaymentService paymentService;

    @BeforeEach
    void setUpMvc() {
        paymentService = new StubPaymentService();
        mockMvc =
                MockMvcBuilders.standaloneSetup(new PaymentController(paymentService))
                        .setControllerAdvice(
                                new ApiResponseMetadataAdvice(), new GlobalExceptionHandler())
                        .addFilters(new RequestIdFilter())
                        .build();
    }

    @Test
    void startPaymentReturns201WithAttempt() throws Exception {
        paymentService.result = sampleAttempt(PaymentAttemptStatus.SUCCESS, OrderStatus.PAID);

        mockMvc.perform(
                        post("/orders/1/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ \"desiredOutcome\": \"SUCCESS\" }"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.gatewayReference").value("gw-1"))
                .andExpect(jsonPath("$.data.amountInCents").value(3998))
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"));

        assertThat(paymentService.capturedOrderId).isEqualTo(1L);
        assertThat(paymentService.capturedDesiredOutcome).isEqualTo(PaymentAttemptStatus.SUCCESS);
    }

    @Test
    void startPaymentWorksWithoutBody() throws Exception {
        paymentService.result = sampleAttempt(PaymentAttemptStatus.PENDING, OrderStatus.RESERVED);

        mockMvc.perform(post("/orders/1/payments")).andExpect(status().isCreated());

        assertThat(paymentService.capturedDesiredOutcome).isNull();
    }

    @Test
    void resolvePaymentReturns200() throws Exception {
        paymentService.result = sampleAttempt(PaymentAttemptStatus.SUCCESS, OrderStatus.PAID);

        mockMvc.perform(
                        post("/payments/callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        { "gatewayReference": "gw-1", "result": "SUCCESS",
                                          "messageId": "msg-1" }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.orderStatus").value("PAID"));

        assertThat(paymentService.capturedGatewayReference).isEqualTo("gw-1");
        assertThat(paymentService.capturedResult).isEqualTo(PaymentAttemptStatus.SUCCESS);
        assertThat(paymentService.capturedMessageId).isEqualTo("msg-1");
    }

    @Test
    void callbackRejectsPendingResult() throws Exception {
        mockMvc.perform(
                        post("/payments/callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        { "gatewayReference": "gw-1", "result": "PENDING",
                                          "messageId": "msg-1" }
                                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem:validation-error"));
    }

    @Test
    void callbackRejectsMissingFields() throws Exception {
        mockMvc.perform(
                        post("/payments/callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ \"result\": \"SUCCESS\" }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem:validation-error"));
    }

    @Test
    void mapsOrderAlreadyPaidTo409() throws Exception {
        paymentService.failure = new OrderAlreadyPaidException(1L);

        mockMvc.perform(
                        post("/orders/1/payments")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:problem:order-already-paid"))
                .andExpect(jsonPath("$.orderId").value(1));
    }

    @Test
    void mapsPaymentAttemptNotFoundTo404() throws Exception {
        paymentService.failure = new PaymentAttemptNotFoundException("gw-unknown");

        mockMvc.perform(
                        post("/payments/callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        { "gatewayReference": "gw-unknown", "result": "SUCCESS",
                                          "messageId": "msg-1" }
                                        """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:problem:payment-attempt-not-found"))
                .andExpect(jsonPath("$.gatewayReference").value("gw-unknown"));
    }

    @Test
    void mapsPaymentConflictTo409() throws Exception {
        paymentService.failure =
                new PaymentConflictException(
                        "gw-1", PaymentAttemptStatus.SUCCESS, PaymentAttemptStatus.DECLINED);

        mockMvc.perform(
                        post("/payments/callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        { "gatewayReference": "gw-1", "result": "DECLINED",
                                          "messageId": "msg-2" }
                                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:problem:payment-conflict"))
                .andExpect(jsonPath("$.gatewayReference").value("gw-1"));
    }

    private static PaymentAttempt sampleAttempt(
            PaymentAttemptStatus status, OrderStatus orderStatus) {
        Order order = new Order();
        order.setStatus(orderStatus);

        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setOrder(order);
        attempt.setAttemptNumber(1);
        attempt.setStatus(status);
        attempt.setGatewayReference("gw-1");
        attempt.setAmountInCents(Money.ofCents(3998));
        attempt.setCreatedAt(Instant.parse("2026-07-22T10:00:00Z"));
        return attempt;
    }

    static final class StubPaymentService extends PaymentService {

        private PaymentAttempt result;
        private RuntimeException failure;
        private Long capturedOrderId;
        private PaymentAttemptStatus capturedDesiredOutcome;
        private String capturedGatewayReference;
        private PaymentAttemptStatus capturedResult;
        private String capturedMessageId;

        private StubPaymentService() {
            super(null, null, null, null, null, null, null);
        }

        @Override
        public PaymentAttempt startPayment(long orderId, PaymentAttemptStatus desiredOutcome) {
            capturedOrderId = orderId;
            capturedDesiredOutcome = desiredOutcome;
            if (failure != null) {
                throw failure;
            }
            return result;
        }

        @Override
        public PaymentAttempt resolvePayment(
                String gatewayReference, PaymentAttemptStatus result, String messageId) {
            capturedGatewayReference = gatewayReference;
            capturedResult = result;
            capturedMessageId = messageId;
            if (failure != null) {
                throw failure;
            }
            return this.result;
        }
    }
}
