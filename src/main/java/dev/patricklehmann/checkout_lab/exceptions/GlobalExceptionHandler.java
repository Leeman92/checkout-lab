package dev.patricklehmann.checkout_lab.exceptions;

import dev.patricklehmann.checkout_lab.exceptions.orders.IdempotencyConflictException;
import dev.patricklehmann.checkout_lab.exceptions.orders.InsufficientStockException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderNotFoundException;
import dev.patricklehmann.checkout_lab.exceptions.orders.OrderValidationException;
import dev.patricklehmann.checkout_lab.exceptions.product.ProductAlreadyExistsException;
import dev.patricklehmann.checkout_lab.exceptions.product.ProductNotFoundException;
import dev.patricklehmann.checkout_lab.filters.RequestIdFilter;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Central translation of domain and framework exceptions into RFC 7807 {@link ProblemDetail}
 * responses. Each domain exception maps to a stable {@code urn:problem:<slug>} type and a fixed
 * HTTP status, so clients can branch on a durable identifier rather than a status code or message.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} lets us also intercept Spring's own MVC
 * exceptions (e.g. body validation). Every problem is funnelled through the overridden {@link
 * #createResponseEntity} so it is uniformly enriched with request id, timestamp, and instance.
 */
@NullMarked
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(ProductNotFoundException.class)
    protected ResponseEntity<Object> handleProductNotFound(
            ProductNotFoundException exception, WebRequest request) {
        log.info("Product not found: sku={}", exception.getSku());

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());

        problem.setTitle("Product not found");
        problem.setType(URI.create("urn:problem:product-not-found"));
        problem.setProperty("sku", exception.getSku());

        return createResponseEntity(problem, HttpHeaders.EMPTY, HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(ProductAlreadyExistsException.class)
    protected ResponseEntity<Object> handleProductAlreadyExists(
            ProductAlreadyExistsException exception, WebRequest request) {
        log.info("Product creation conflict: {}", exception.getMessage());

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());

        problem.setTitle("Product already exists");
        problem.setType(URI.create("urn:problem:product-already-exists"));

        return createResponseEntity(problem, HttpHeaders.EMPTY, HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    protected ResponseEntity<Object> handleOrderNotFound(
            OrderNotFoundException exception, WebRequest request) {
        log.info("Order not found: orderId={}", exception.getOrderId());

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());

        problem.setTitle("Order not found");
        problem.setType(URI.create("urn:problem:order-not-found"));
        problem.setProperty("orderId", exception.getOrderId());

        return createResponseEntity(problem, HttpHeaders.EMPTY, HttpStatus.NOT_FOUND, request);
    }

    @ExceptionHandler(OrderValidationException.class)
    protected ResponseEntity<Object> handleOrderValidation(
            OrderValidationException exception, WebRequest request) {
        log.info("Order rejected: {} validation error(s)", exception.getErrors().size());

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "The order request contains one or more invalid lines.");

        problem.setTitle("Order validation failed");
        problem.setType(URI.create("urn:problem:order-validation"));
        problem.setProperty("errors", exception.getErrors());

        return createResponseEntity(
                problem, HttpHeaders.EMPTY, HttpStatus.UNPROCESSABLE_ENTITY, request);
    }

    @ExceptionHandler(InsufficientStockException.class)
    protected ResponseEntity<Object> handleInsufficientStock(
            InsufficientStockException exception, WebRequest request) {
        log.info(
                "Order rejected: insufficient stock for {} line(s)",
                exception.getShortfalls().size());

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.CONFLICT,
                        "One or more order lines could not be reserved from available stock.");

        problem.setTitle("Insufficient stock");
        problem.setType(URI.create("urn:problem:insufficient-stock"));
        problem.setProperty("errors", exception.getShortfalls());

        return createResponseEntity(problem, HttpHeaders.EMPTY, HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    protected ResponseEntity<Object> handleIdempotencyConflict(
            IdempotencyConflictException exception, WebRequest request) {
        log.info("Order rejected: idempotency conflict key={}", exception.getIdempotencyKey());

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());

        problem.setTitle("Idempotency key conflict");
        problem.setType(URI.create("urn:problem:idempotency-conflict"));
        problem.setProperty("idempotencyKey", exception.getIdempotencyKey());

        return createResponseEntity(problem, HttpHeaders.EMPTY, HttpStatus.CONFLICT, request);
    }

    /**
     * Last-resort fallback for unexpected errors during request processing. Returns a sanitized 500
     * that never leaks the underlying exception detail (ER-003); the request id lets the real cause
     * be found in the logs.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(
            Exception exception, WebRequest request) {
        log.error("Unhandled exception while processing request", exception);

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        """
            Something went wrong. If the error persists, please contact \
            support and provide the request ID.
            """);

        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("urn:problem:internal-server-error"));

        return createResponseEntity(
                problem, HttpHeaders.EMPTY, HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    /**
     * Overrides Spring's bean-validation handling to emit a flat field→message {@code errors} map
     * (400, {@code urn:problem:validation-error}) instead of the default verbose payload. The first
     * message per field wins, keeping the response stable and easy for clients to consume.
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
            fieldErrors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }

        log.info("Request validation failed: fields={}", fieldErrors.keySet());

        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.BAD_REQUEST, "The request contains invalid fields.");

        problem.setTitle("Validation failed");
        problem.setType(URI.create("urn:problem:validation-error"));
        problem.setProperty("errors", fieldErrors);

        return createResponseEntity(problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    /**
     * Single choke point for every response this handler produces. Enriches any {@link
     * ProblemDetail} body with the correlation {@code requestId}, a {@code timestamp}, a sensible
     * default {@code type} when none was set, and an {@code instance} pointing at the request URI —
     * so those fields never have to be repeated in each handler. Non-problem bodies pass through
     * unchanged.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(
            @Nullable Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        if (body instanceof ProblemDetail problem
                && request instanceof ServletWebRequest servletRequest) {

            HttpServletRequest httpRequest = servletRequest.getRequest();

            Object requestId = httpRequest.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
            if (requestId != null) {
                problem.setProperty("requestId", requestId);
            }

            Object type = problem.getType();
            if (type == null) {
                String uri;
                if (statusCode.is4xxClientError()) {
                    uri = "urn:problem:client-error";
                    if (statusCode.isSameCodeAs(HttpStatus.NOT_FOUND)) {
                        uri = "urn:problem:not-found";
                    }
                } else {
                    uri = "urn:problem:internal-server-error";
                }
                problem.setType(URI.create(uri));
            }

            problem.setProperty("timestamp", Instant.now());

            if (problem.getInstance() == null) {
                problem.setInstance(URI.create(httpRequest.getRequestURI()));
            }
        }

        return super.createResponseEntity(body, headers, statusCode, request);
    }
}
