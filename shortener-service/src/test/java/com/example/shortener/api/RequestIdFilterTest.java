package com.example.shortener.api;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void generatesRequestIdWhenNoneProvided() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(RequestIdFilter.HEADER))
                .as("a UUID is set when the caller provides no X-Request-Id")
                .isNotNull()
                .matches("[0-9a-f-]{36}");
    }

    @Test
    void propagatesRequestIdFromCaller() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "upstream-trace-abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("upstream-trace-abc");
    }

    @Test
    void treatsBlankHeaderAsAbsent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "   ");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(RequestIdFilter.HEADER))
                .as("blank header value is replaced with a generated UUID")
                .isNotBlank()
                .doesNotContain(" ");
    }

    @Test
    void rejectsUnsafeRequestIdToPreventHeaderInjection() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER, "abc\r\nX-Injected: evil");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(response.getHeader(RequestIdFilter.HEADER))
                .as("ID containing CR/LF must be replaced with a safe UUID to prevent response splitting")
                .matches("[0-9a-f-]{36}")
                .doesNotContain("evil");
    }

    @Test
    void requestIdIsInMdcDuringChainExecution() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> capturedId = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> capturedId.set(MDC.get(RequestIdFilter.MDC_KEY)));

        assertThat(capturedId.get()).isNotNull();
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo(capturedId.get());
    }

    @Test
    void requestIdIsRemovedFromMdcAfterRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(MDC.get(RequestIdFilter.MDC_KEY))
                .as("MDC must be cleaned up to avoid leaking into other requests on the same thread")
                .isNull();
    }

    @Test
    void requestIdIsRemovedFromMdcEvenWhenChainThrows() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
            throw new IOException("downstream failure");
        })).isInstanceOf(IOException.class);

        assertThat(MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }
}
