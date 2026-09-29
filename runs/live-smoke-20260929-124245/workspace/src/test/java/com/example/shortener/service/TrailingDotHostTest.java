package com.example.shortener.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Regression test for the trailing-dot bypass: an absolute DNS name ({@code localhost.}) resolves exactly like
 * its relative form, so it must be classified the same way.
 */
class TrailingDotHostTest {
    private final UrlValidator validator = new UrlValidator();

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost./admin", "http://LOCALHOST./", "http://api.localhost./",
            "http://printer.local./", "https://nas.local.:8443/share"})
    void absoluteNamesOfLocalHostsAreRejected(String url) {
        ShortenerException rejected = assertThrows(ShortenerException.class, () -> validator.normalize(url),
                url + " must be rejected");
        assertThat(rejected).hasMessageContaining("publicly routable");
    }

    @Test
    void absoluteNamesOfPublicHostsAreStillAccepted() {
        assertThat(validator.normalize("https://example.com./path")).isEqualTo("https://example.com./path");
    }
}
