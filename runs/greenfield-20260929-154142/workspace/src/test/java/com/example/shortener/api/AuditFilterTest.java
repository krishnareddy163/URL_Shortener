package com.example.shortener.api;

import com.example.shortener.domain.AuditEvent;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class AuditFilterTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-10T09:30:00Z"), ZoneOffset.UTC);

    @Test
    void recordsStateChangingRequestsWithTheirFinalStatus() throws Exception {
        List<AuditEvent> events = new ArrayList<>();
        AuditFilter filter = new AuditFilter(events::add, new ClientKeyResolver(), CLOCK);
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/api/v1/links/abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(405);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.occurredAt()).isEqualTo(CLOCK.instant());
            assertThat(event.method()).isEqualTo("DELETE");
            assertThat(event.path()).isEqualTo("/api/v1/links/abc");
            assertThat(event.status()).isEqualTo(405);
            assertThat(event.clientKey()).matches("[0-9a-f]{32}");
        });
    }

    @Test
    void readsAreNotRecorded() throws Exception {
        List<AuditEvent> events = new ArrayList<>();
        AuditFilter filter = new AuditFilter(events::add, new ClientKeyResolver(), CLOCK);

        filter.doFilter(new MockHttpServletRequest("GET", "/abc1234"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(events).isEmpty();
    }

    @Test
    void aFailedAuditWriteDoesNotFailTheRequest() {
        AuditFilter filter = new AuditFilter(event -> {
            throw new IllegalStateException("database down");
        }, new ClientKeyResolver(), CLOCK);

        assertThatCode(() -> filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/links"),
                new MockHttpServletResponse(), new MockFilterChain())).doesNotThrowAnyException();
    }

    @Test
    void longPathsAreTruncated() {
        AuditEvent event = new AuditEvent(CLOCK.instant(), "k", "POST", "/" + "a".repeat(400), 201);

        assertThat(event.path()).hasSize(AuditEvent.MAX_PATH);
    }
}
