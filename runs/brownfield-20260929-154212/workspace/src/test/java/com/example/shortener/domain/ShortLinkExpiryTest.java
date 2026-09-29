package com.example.shortener.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ShortLinkExpiryTest {
    private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

    @Test
    void linkWithoutExpiryNeverExpires() {
        assertThat(new ShortLink("abc1234", "https://example.com/", "h", NOW).isExpiredAt(Instant.MAX)).isFalse();
    }

    @Test
    void linkExpiresExactlyAtItsExpiryInstant() {
        ShortLink link = new ShortLink("abc1234", "https://example.com/", "h", NOW, NOW.plusSeconds(60));
        assertThat(link.isExpiredAt(NOW.plusSeconds(59))).isFalse();
        assertThat(link.isExpiredAt(NOW.plusSeconds(60))).isTrue();
    }
}
