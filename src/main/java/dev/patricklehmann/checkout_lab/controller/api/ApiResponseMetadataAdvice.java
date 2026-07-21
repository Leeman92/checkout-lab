package dev.patricklehmann.checkout_lab.controller.api;

import dev.patricklehmann.checkout_lab.filters.RequestIdFilter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Wraps every successful JSON response body in a consistent {@code {timestamp, requestId, data}}
 * envelope so clients can correlate responses with logs (via the request id) without each
 * controller having to assemble that metadata itself.
 *
 * <p>Error responses are handled separately by {@code GlobalExceptionHandler} (RFC 7807 {@code
 * ProblemDetail}) and are not wrapped here.
 */
@NullMarked
@RestControllerAdvice(basePackages = "dev.patricklehmann.checkout_lab.controller.api")
public class ApiResponseMetadataAdvice implements ResponseBodyAdvice<Object> {

    // Only engage for JSON bodies; other converters (e.g. ProblemDetail, binary) pass through
    // untouched.
    @Override
    public boolean supports(
            MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return JacksonJsonHttpMessageConverter.class.isAssignableFrom(converterType);
    }

    /**
     * Prepends {@code timestamp} and {@code requestId} to the outgoing body. A {@code Map} body is
     * merged in (existing metadata keys are never overwritten); any other body is nested under a
     * {@code data} key. A {@code null} body is left as-is.
     */
    @Override
    public @Nullable Object beforeBodyWrite(
            @Nullable Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body == null) {
            return null;
        }

        String requestId = resolveRequestId(request);

        Map<String, Object> responseBody = new LinkedHashMap<>();

        responseBody.put("timestamp", Instant.now());
        responseBody.put("requestId", requestId);

        if (body instanceof Map<?, ?> originalBody) {
            originalBody.forEach(
                    (key, value) -> {
                        String fieldName = String.valueOf(key);

                        // Globale Metadaten sollen nicht überschrieben werden.
                        if (!responseBody.containsKey(fieldName)) {
                            responseBody.put(fieldName, value);
                        }
                    });
        } else {
            responseBody.put("data", body);
        }

        return responseBody;
    }

    private @Nullable String resolveRequestId(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            return null;
        }

        Object requestId =
                servletRequest.getServletRequest().getAttribute(RequestIdFilter.ATTRIBUTE_NAME);

        return requestId != null ? requestId.toString() : null;
    }
}
