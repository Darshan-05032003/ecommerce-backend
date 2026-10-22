package com.darshan.ecommerce.common.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/products");
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final AtomicReference<String> idSeenDownstream = new AtomicReference<>();
    private final FilterChain chain = (req, res) -> idSeenDownstream.set(MDC.get(CorrelationIdFilter.MDC_KEY));

    @Test
    void reusesTheIncomingHeaderValue() throws Exception {
        request.addHeader(CorrelationIdFilter.HEADER, "client-id-42");

        filter.doFilter(request, response, chain);

        assertThat(idSeenDownstream).hasValue("client-id-42");
        assertThat(response.getHeader(CorrelationIdFilter.HEADER)).isEqualTo("client-id-42");
    }

    @Test
    void generatesAUuidWhenTheHeaderIsMissing() throws Exception {
        filter.doFilter(request, response, chain);

        String generated = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(generated).isNotNull();
        assertThat(UUID.fromString(generated)).hasToString(generated);
        assertThat(idSeenDownstream).hasValue(generated);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "has spaces", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "line\nbreak", "<script>"})
    void replacesHeaderValuesThatAreNotSafeToLog(String unsafeValue) throws Exception {
        request.addHeader(CorrelationIdFilter.HEADER, unsafeValue);

        filter.doFilter(request, response, chain);

        String used = response.getHeader(CorrelationIdFilter.HEADER);
        assertThat(used).isNotEqualTo(unsafeValue);
        assertThat(UUID.fromString(used)).hasToString(used);
    }

    @Test
    void clearsTheMdcAfterTheRequestEvenWhenDownstreamFails() {
        request.addHeader(CorrelationIdFilter.HEADER, "client-id-42");
        FilterChain failingChain = (req, res) -> {
            throw new IllegalStateException("boom");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, failingChain))
                .isInstanceOf(IllegalStateException.class);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void clearsTheMdcAfterANormalRequest() throws Exception {
        filter.doFilter(request, response, chain);

        assertThat(MDC.get(CorrelationIdFilter.MDC_KEY)).isNull();
    }
}
