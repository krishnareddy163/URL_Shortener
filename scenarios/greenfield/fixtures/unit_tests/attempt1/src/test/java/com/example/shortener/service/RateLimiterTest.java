package com.example.shortener.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimiterTest {
    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final RateLimiter limiter = new RateLimiter(20, clock);

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        for (int request = 0; request < 20; request++) {
            assertThat(limiter.tryAcquire("client")).as("request %d", request).isTrue();
        }
        assertThat(limiter.tryAcquire("client")).isFalse();
    }

    @Test
    void refillsOneTokenEveryThreeSeconds() {
        exhaust("client");

        clock.advance(Duration.ofMillis(2_999));
        assertThat(limiter.tryAcquire("client")).isFalse();

        clock.advance(Duration.ofMillis(1));
        assertThat(limiter.tryAcquire("client")).isTrue();
        assertThat(limiter.tryAcquire("client")).isFalse();
    }

    @Test
    void refillIsCappedAtCapacity() {
        exhaust("client");
        clock.advance(Duration.ofHours(1));

        exhaust("client");
        assertThat(limiter.tryAcquire("client")).isFalse();
    }

    @Test
    void bucketsAreIndependentPerClientKey() {
        exhaust("first");

        assertThat(limiter.tryAcquire("second")).isTrue();
        assertThat(limiter.tryAcquire("first")).isFalse();
    }

    @Test
    void rejectsNonPositiveConfiguration() {
        assertThatThrownBy(() -> new RateLimiter(0, clock)).isInstanceOf(IllegalArgumentException.class);
    }

    private void exhaust(String key) {
        for (int request = 0; request < 20; request++) {
            assertThat(limiter.tryAcquire(key)).isTrue();
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
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
