package dev.patricklehmann.checkout_lab.exceptions;

import dev.patricklehmann.checkout_lab.exceptions.product.ProductAlreadyExistsException;
import dev.patricklehmann.checkout_lab.exceptions.product.ProductNotFoundException;
import dev.patricklehmann.checkout_lab.filters.RequestIdFilter;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NullMarked;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

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

    /** Letzter Fallback für unerwartete Fehler während der MVC-Verarbeitung. */
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
