package dev.patricklehmann.checkout_lab.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.jspecify.annotations.NonNull;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Request-ID";
    public static final String ATTRIBUTE_NAME = "requestId";
    public static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = resolveRequestId(request);

        request.setAttribute(ATTRIBUTE_NAME, requestId);
        response.setHeader(HEADER_NAME, requestId);
        MDC.put(MDC_KEY, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String candidate = request.getHeader(HEADER_NAME);

        if (candidate == null || candidate.length() != 36) {
            return UUID.randomUUID().toString();
        }

        try {
            UUID uuid = UUID.fromString(candidate);

            // Return a newly generated canonical string rather than client input.
            return uuid.toString();
        } catch (IllegalArgumentException exception) {
            return UUID.randomUUID().toString();
        }
    }
}
