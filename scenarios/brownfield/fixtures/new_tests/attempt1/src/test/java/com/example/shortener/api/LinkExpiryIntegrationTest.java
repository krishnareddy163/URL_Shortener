package com.example.shortener.api;

import com.jayway.jsonpath.JsonPath;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:expiry;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@Import(LinkExpiryIntegrationTest.TestClock.class)
class LinkExpiryIntegrationTest {
    static final MutableClock CLOCK = new MutableClock(Instant.parse("2026-06-01T12:00:00Z"));

    @Autowired
    private MockMvc mvc;

    @Test
    void linkRedirectsUntilItExpiresThenReturns410() throws Exception {
        Instant expiresAt = CLOCK.instant().plus(Duration.ofHours(1));
        String body = create("{\"url\":\"https://example.com/expiring\",\"expiresAt\":\"" + expiresAt + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").value(expiresAt.toString()))
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(body, "$.code");

        mvc.perform(get("/" + code)).andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/expiring"));

        CLOCK.advance(Duration.ofHours(1));
        mvc.perform(get("/" + code))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.error.code").value("LINK_EXPIRED"));
        mvc.perform(get("/api/v1/links/" + code + "/stats")).andExpect(status().isOk());
    }

    @Test
    void linkWithoutExpiryBehavesLikeV1ForeverAndOmitsTheField() throws Exception {
        String body = create("{\"url\":\"https://example.com/forever\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(body, "$.code");

        CLOCK.advance(Duration.ofDays(3650));
        mvc.perform(get("/" + code)).andExpect(status().isFound());
    }

    @Test
    void pastOrPresentExpiryIsRejected() throws Exception {
        create("{\"url\":\"https://example.com/past\",\"expiresAt\":\"" + CLOCK.instant() + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_EXPIRY"));
        create("{\"url\":\"https://example.com/garbage\",\"expiresAt\":\"tomorrow\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
    }

    @Test
    void sameUrlWithADifferentExpiryConflicts() throws Exception {
        Instant expiresAt = CLOCK.instant().plus(Duration.ofDays(1));
        create("{\"url\":\"https://example.com/dup\",\"expiresAt\":\"" + expiresAt + "\"}").andExpect(status().isCreated());

        create("{\"url\":\"https://example.com/dup\",\"expiresAt\":\"" + expiresAt + "\"}").andExpect(status().isOk());
        create("{\"url\":\"https://example.com/dup\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("URL_ALREADY_SHORTENED"));
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(post("/api/v1/links").contentType(MediaType.APPLICATION_JSON).content(json));
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
