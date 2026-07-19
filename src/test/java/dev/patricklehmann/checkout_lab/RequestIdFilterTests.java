package dev.patricklehmann.checkout_lab;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patricklehmann.checkout_lab.filters.RequestIdFilter;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTests {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void preservesValidRequestIdForRequestResponseAndLogging() throws Exception {
        String requestId = "59edc107-a937-40e0-b387-3d342053a238";
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(RequestIdFilter.HEADER_NAME, requestId);

        filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> {
                    assertThat(servletRequest.getAttribute(RequestIdFilter.ATTRIBUTE_NAME))
                            .isEqualTo(requestId);
                    assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isEqualTo(requestId);
                });

        assertThat(response.getHeader(RequestIdFilter.HEADER_NAME)).isEqualTo(requestId);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void generatesCanonicalRequestIdWhenHeaderIsInvalid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader(RequestIdFilter.HEADER_NAME, "invalid-request-id");

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {});

        String requestId = response.getHeader(RequestIdFilter.HEADER_NAME);

        assertThat(requestId).isNotNull();
        assertThat(UUID.fromString(requestId).toString()).isEqualTo(requestId);
        assertThat(request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME)).isEqualTo(requestId);
        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
