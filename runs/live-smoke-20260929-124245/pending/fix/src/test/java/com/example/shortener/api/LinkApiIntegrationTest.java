package com.example.shortener.api;

import com.jayway.jsonpath.JsonPath;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(LinkApiIntegrationTest.TestClock.class)
class LinkApiIntegrationTest {
    static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-03-10T09:30:00Z"));

    @Autowired
    private MockMvc mvc;

    @Test
    void createThenRedirectReturns302WithLocation() throws Exception {
        String code = createdCode(create("{\"url\":\"https://example.com/docs?page=1\"}", "198.51.100.1")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.matchesPattern("[0-9A-Za-z]{7}")))
                .andExpect(jsonPath("$.url").value("https://example.com/docs?page=1"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty()));

        mvc.perform(get("/" + code))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/docs?page=1"));
    }

    @Test
    void shortUrlPointsAtTheRedirectEndpoint() throws Exception {
        String body = create("{\"url\":\"https://example.com/short-url\"}", "198.51.100.2")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(body, "$.code");

        assertThat((String) JsonPath.read(body, "$.shortUrl")).isEqualTo("http://localhost/" + code);
    }

    @Test
    void unknownCodeReturns404WithErrorEnvelope() throws Exception {
        mvc.perform(get("/nope404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("LINK_NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").isNotEmpty());
    }

    @Test
    void creatingTheSameNormalizedUrlAgainIsIdempotent() throws Exception {
        String code = createdCode(create("{\"url\":\"https://example.com/idempotent\"}", "198.51.100.3")
                .andExpect(status().isCreated()));

        create("{\"url\":\"HTTPS://EXAMPLE.com:443/idempotent\"}", "198.51.100.3")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));
    }

    @Test
    void duplicateAliasReturns409() throws Exception {
        create("{\"url\":\"https://example.com/one\",\"customAlias\":\"team-docs\"}", "198.51.100.4")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("team-docs"));

        create("{\"url\":\"https://example.com/two\",\"customAlias\":\"team-docs\"}", "198.51.100.4")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALIAS_TAKEN"));
    }

    @Test
    void invalidUrlReturns400() throws Exception {
        create("{\"url\":\"http://169.254.169.254/latest\"}", "198.51.100.5")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_URL"));
        create("{\"url\":\"ftp://example.com/file\"}", "198.51.100.5")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_URL"));
    }

    @Test
    void invalidAliasAndMalformedBodiesReturn400() throws Exception {
        create("{\"url\":\"https://example.com/x\",\"customAlias\":\"api\"}", "198.51.100.6")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_ALIAS"));
        create("{\"url\":\"\"}", "198.51.100.6")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
        create("{\"url\":\"https://example.com/x\",\"unexpected\":true}", "198.51.100.6")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void rateLimitReturns429AfterTwentyCreatesPerMinute() throws Exception {
        for (int request = 0; request < 20; request++) {
            create("{\"url\":\"https://example.com/rate/" + request + "\"}", "198.51.100.7")
                    .andExpect(status().isCreated());
        }

        create("{\"url\":\"https://example.com/rate/overflow\"}", "198.51.100.7")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("RATE_LIMITED"));
        create("{\"url\":\"https://example.com/rate/other-client\"}", "198.51.100.8")
                .andExpect(status().isCreated());
    }

    @Test
    void statsReportTotalsLastAccessAndPerDayBuckets() throws Exception {
        String code = createdCode(create("{\"url\":\"https://example.com/stats\"}", "198.51.100.9")
                .andExpect(status().isCreated()));
        mvc.perform(get("/api/v1/links/" + code + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(0))
                .andExpect(jsonPath("$.lastAccessedAt").doesNotExist())
                .andExpect(jsonPath("$.clicksPerDay").isEmpty());

        LocalDate firstDay = LocalDate.ofInstant(CLOCK.instant(), ZoneOffset.UTC);
        mvc.perform(get("/" + code)).andExpect(status().isFound());
        mvc.perform(get("/" + code)).andExpect(status().isFound());
        CLOCK.advance(Duration.ofDays(1));
        Instant lastClick = CLOCK.instant();
        mvc.perform(get("/" + code)).andExpect(status().isFound());

        awaitTotalClicks(code, 3);
        mvc.perform(get("/api/v1/links/" + code + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.totalClicks").value(3))
                .andExpect(jsonPath("$.lastAccessedAt").value(lastClick.toString()))
                .andExpect(jsonPath("$.clicksPerDay.length()").value(2))
                .andExpect(jsonPath("$.clicksPerDay[0].date").value(firstDay.toString()))
                .andExpect(jsonPath("$.clicksPerDay[0].count").value(2))
                .andExpect(jsonPath("$.clicksPerDay[1].date").value(firstDay.plusDays(1).toString()))
                .andExpect(jsonPath("$.clicksPerDay[1].count").value(1));
    }

    @Test
    void statsForUnknownCodeReturns404() throws Exception {
        mvc.perform(get("/api/v1/links/unknown/stats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("LINK_NOT_FOUND"));
    }

    @Test
    void responsesAreNotCacheableAndNotSniffable() throws Exception {
        mvc.perform(get("/api/v1/links/unknown/stats"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    void unmappedRoutesAndMethodsUseTheErrorEnvelope() throws Exception {
        mvc.perform(get("/a/b/c"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
        mvc.perform(get("/api/v1/links"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    private ResultActions create(String json, String remoteAddress) throws Exception {
        return mvc.perform(post("/api/v1/links").with(remoteAddr(remoteAddress))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private static String createdCode(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.code");
    }

    private void awaitTotalClicks(String code, int expected) {
        Awaitility.await().atMost(Duration.ofSeconds(5)).pollInterval(Duration.ofMillis(20)).untilAsserted(() ->
                mvc.perform(get("/api/v1/links/" + code + "/stats")).andExpect(jsonPath("$.totalClicks").value(expected)));
    }

    private static RequestPostProcessor remoteAddr(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    @TestConfiguration
    static class TestClock {
        @Bean
        @Primary
        Clock testClock() {
            return CLOCK;
        }
    }

    static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
