package dev.patricklehmann.checkout_lab;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.patricklehmann.checkout_lab.controller.api.ApiResponseMetadataAdvice;
import dev.patricklehmann.checkout_lab.controller.api.orders.OrderController;
import dev.patricklehmann.checkout_lab.controller.api.orders.dto.CreateOrderRequest;
import dev.patricklehmann.checkout_lab.entities.orders.Order;
import dev.patricklehmann.checkout_lab.entities.orders.OrderItem;
import dev.patricklehmann.checkout_lab.entities.orders.OrderStatus;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttempt;
import dev.patricklehmann.checkout_lab.entities.payments.PaymentAttemptStatus;
import dev.patricklehmann.checkout_lab.entities.shared.Money;
import dev.patricklehmann.checkout_lab.entities.shared.Sku;
import dev.patricklehmann.checkout_lab.exceptions.GlobalExceptionHandler;
import dev.patricklehmann.checkout_lab.exceptions.orders.IdempotencyConflictException;
import dev.patricklehmann.checkout_lab.exceptions.orders.InsufficientStockException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderNotFoundException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderTransitionException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderValidationError;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderValidationException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderValidationReason;
import dev.patricklehmann.checkout_lab.exceptions.orders.StockShortfall;
import dev.patricklehmann.checkout_lab.filters.RequestIdFilter;
import dev.patricklehmann.checkout_lab.services.orders.OrderCreationResult;
import dev.patricklehmann.checkout_lab.services.orders.OrderService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OrderControllerTests {

    private static final String REQUEST_ID = "59edc107-a937-40e0-b387-3d342053a238";
    private static final String KEY = "11111111-1111-1111-1111-111111111111";

    private MockMvc mockMvc;
    private StubOrderService orderService;

    @BeforeEach
    void setUpMvc() {
        orderService = new StubOrderService();
        mockMvc =
                MockMvcBuilders.standaloneSetup(new OrderController(orderService))
                        .setControllerAdvice(
                                new ApiResponseMetadataAdvice(), new GlobalExceptionHandler())
                        .addFilters(new RequestIdFilter())
                        .build();
    }

    @Test
    void createsOrderReturns201WithMetadataEnvelope() throws Exception {
        orderService.result = new OrderCreationResult(sampleOrder(), false);

        mockMvc.perform(
                        post("/orders")
                                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                                .header("Idempotency-Key", KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        { "items": [ { "sku": "TSHIRT-BLK-M", "quantity": 2 } ] }
                                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.currency").value("EUR"))
                .andExpect(jsonPath("$.data.totalNetInCents").value(3998))
                .andExpect(jsonPath("$.data.items[0].sku").value("TSHIRT-BLK-M"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2));
    }

    @Test
    void passesIdempotencyKeyAndRequestToService() throws Exception {
        orderService.result = new OrderCreationResult(sampleOrder(), false);

        mockMvc.perform(
                        post("/orders")
                                .header("Idempotency-Key", KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        { "items": [ { "sku": "tshirt-blk-m", "quantity": 2 } ] }
                                        """))
                .andExpect(status().isCreated());

        org.assertj.core.api.Assertions.assertThat(orderService.capturedKey).isEqualTo(KEY);
        org.assertj.core.api.Assertions.assertThat(orderService.capturedRequest.items())
                .singleElement()
                .satisfies(
                        item -> {
                            org.assertj.core.api.Assertions.assertThat(item.sku())
                                    .isEqualTo("tshirt-blk-m");
                            org.assertj.core.api.Assertions.assertThat(item.quantity())
                                    .isEqualTo(2);
                        });
    }

    @Test
    void replayReturns200() throws Exception {
        orderService.result = new OrderCreationResult(sampleOrder(), true);

        mockMvc.perform(
                        post("/orders")
                                .header("Idempotency-Key", KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        { "items": [ { "sku": "TSHIRT-BLK-M", "quantity": 2 } ] }
                                        """))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsRequestWithNoItems() throws Exception {
        mockMvc.perform(
                        post("/orders")
                                .header("Idempotency-Key", KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{ \"items\": [] }"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:problem:validation-error"));
    }

    @Test
    void rejectsMissingIdempotencyKeyHeader() throws Exception {
        mockMvc.perform(
                        post("/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        { "items": [ { "sku": "TSHIRT-BLK-M", "quantity": 2 } ] }
                                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mapsOrderValidationExceptionTo422WithErrorList() throws Exception {
        orderService.failure =
                new OrderValidationException(
                        List.of(
                                new OrderValidationError("NOPE", OrderValidationReason.UNKNOWN_SKU),
                                new OrderValidationError(
                                        "BELT-BRN-100", OrderValidationReason.INACTIVE_PRODUCT)));

        mockMvc.perform(
                        post("/orders")
                                .header("Idempotency-Key", KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        { "items": [ { "sku": "NOPE", "quantity": 1 } ] }
                                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.type").value("urn:problem:order-validation"))
                .andExpect(jsonPath("$.errors.length()").value(2))
                .andExpect(jsonPath("$.errors[0].sku").value("NOPE"))
                .andExpect(jsonPath("$.errors[0].reason").value("UNKNOWN_SKU"));
    }

    @Test
    void mapsInsufficientStockExceptionTo409WithErrorList() throws Exception {
        orderService.failure =
                new InsufficientStockException(List.of(new StockShortfall("TSHIRT-BLK-M", 2, 1)));

        mockMvc.perform(
                        post("/orders")
                                .header("Idempotency-Key", KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        { "items": [ { "sku": "TSHIRT-BLK-M", "quantity": 2 } ] }
                                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:problem:insufficient-stock"))
                .andExpect(jsonPath("$.errors[0].sku").value("TSHIRT-BLK-M"))
                .andExpect(jsonPath("$.errors[0].requested").value(2))
                .andExpect(jsonPath("$.errors[0].available").value(1));
    }

    @Test
    void mapsIdempotencyConflictTo409() throws Exception {
        orderService.failure = new IdempotencyConflictException(KEY);

        mockMvc.perform(
                        post("/orders")
                                .header("Idempotency-Key", KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        { "items": [ { "sku": "TSHIRT-BLK-M", "quantity": 2 } ] }
                                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:problem:idempotency-conflict"))
                .andExpect(jsonPath("$.idempotencyKey").value(KEY));
    }

    @Test
    void getOrderReturnsOrder() throws Exception {
        orderService.order = sampleOrder();

        mockMvc.perform(get("/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESERVED"))
                .andExpect(jsonPath("$.data.items[0].sku").value("TSHIRT-BLK-M"));
    }

    @Test
    void getOrderSurfacesPaymentAttempts() throws Exception {
        orderService.order = sampleOrder();
        orderService.paymentAttempts = List.of(sampleAttempt());

        mockMvc.perform(get("/orders/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payments.length()").value(1))
                .andExpect(jsonPath("$.data.payments[0].attemptNumber").value(1))
                .andExpect(jsonPath("$.data.payments[0].status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.payments[0].amountInCents").value(3998));
    }

    @Test
    void cancelOrderReturnsCancelledOrder() throws Exception {
        Order cancelled = sampleOrder();
        cancelled.setStatus(OrderStatus.CANCELLED);
        orderService.order = cancelled;

        mockMvc.perform(post("/orders/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void cancelPaidOrderReturns409() throws Exception {
        orderService.failure =
                new OrderTransitionException("key", OrderStatus.PAID, OrderStatus.CANCELLED);

        mockMvc.perform(post("/orders/1/cancel"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:problem:order-transition"));
    }

    @Test
    void getUnknownOrderReturns404() throws Exception {
        orderService.failure = new OrderNotFoundException(999L);

        mockMvc.perform(get("/orders/999").header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:problem:order-not-found"))
                .andExpect(jsonPath("$.orderId").value(999));
    }

    private static Order sampleOrder() {
        Order order = new Order();
        order.setStatus(OrderStatus.RESERVED);
        order.setCurrency("EUR");
        order.setCreatedAt(Instant.parse("2026-07-22T10:00:00Z"));
        order.setUpdatedAt(Instant.parse("2026-07-22T10:00:00Z"));
        order.setIdempotencyKey(KEY);
        order.setRequestFingerprint("fingerprint");
        order.setTotalNetInCents(Money.ofCents(3998));

        OrderItem item = new OrderItem();
        item.setSku(new Sku("TSHIRT-BLK-M"));
        item.setQuantity(2);
        item.setUnitNetPriceInCents(Money.ofCents(1999));
        item.setLineNetInCents(Money.ofCents(3998));
        order.addItem(item);

        return order;
    }

    private static PaymentAttempt sampleAttempt() {
        PaymentAttempt attempt = new PaymentAttempt();
        attempt.setAttemptNumber(1);
        attempt.setStatus(PaymentAttemptStatus.SUCCESS);
        attempt.setAmountInCents(Money.ofCents(3998));
        attempt.setCreatedAt(Instant.parse("2026-07-22T10:00:00Z"));
        attempt.setResolvedAt(Instant.parse("2026-07-22T10:00:05Z"));
        return attempt;
    }

    static final class StubOrderService extends OrderService {

        private OrderCreationResult result;
        private Order order;
        private List<PaymentAttempt> paymentAttempts = List.of();
        private String capturedKey;
        private CreateOrderRequest capturedRequest;
        private RuntimeException failure;

        private StubOrderService() {
            super(null, null, null, null, null);
        }

        @Override
        public OrderCreationResult createOrder(String idempotencyKey, CreateOrderRequest request) {
            capturedKey = idempotencyKey;
            capturedRequest = request;
            if (failure != null) {
                throw failure;
            }
            return result;
        }

        @Override
        public Order getOrder(long orderId) {
            if (failure != null) {
                throw failure;
            }
            return order;
        }

        @Override
        public List<PaymentAttempt> getPaymentAttempts(Order order) {
            return paymentAttempts;
        }

        @Override
        public Order cancelOrder(long orderId) {
            if (failure != null) {
                throw failure;
            }
            return order;
        }
    }
}
