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

/**
 * Assigns a correlation id to every request. Reads a client-supplied {@code X-Request-ID}, or
 * generates one, echoes it back on the response header, exposes it as a request attribute (consumed
 * by the response envelope and problem details), and binds it into the SLF4J {@link MDC} so all log
 * lines for the request carry it. The MDC entry is always cleared in a {@code finally} block to
 * avoid leaking the id onto the thread's next request.
 */
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

    /**
     * Returns a trusted correlation id: the client header only if it parses as a canonical 36-char
     * UUID, otherwise a freshly generated one. Even a valid client value is re-serialized from the
     * parsed UUID so the echoed id is always canonical and never raw client input.
     */
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
