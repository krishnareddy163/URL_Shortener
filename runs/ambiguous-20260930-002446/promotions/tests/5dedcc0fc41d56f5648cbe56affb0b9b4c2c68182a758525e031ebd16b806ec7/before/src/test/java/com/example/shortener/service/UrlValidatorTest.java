package com.example.shortener.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UrlValidatorTest {
    private final UrlValidator validator = new UrlValidator();

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsMissingUrl(String url) {
        assertInvalid(url, "required");
    }

    @Test
    void rejectsUrlsLongerThan2048Characters() {
        String url = "https://example.com/" + "a".repeat(2029);
        assertThat(url).hasSize(2049);
        assertInvalid(url, "2048");
    }

    @Test
    void acceptsUrlOfExactly2048Characters() {
        String url = "https://example.com/" + "a".repeat(2028);
        assertThat(validator.normalize(url)).hasSize(2048);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ftp://example.com/file", "file:///etc/passwd", "javascript:alert(1)",
            "mailto:someone@example.com", "example.com/no-scheme"})
    void rejectsNonHttpSchemes(String url) {
        assertInvalid(url, "scheme");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https:///missing-host", "http://"})
    void requiresHost(String url) {
        assertThatThrownBy(() -> validator.normalize(url)).isInstanceOf(ShortenerException.class)
                .extracting(error -> ((ShortenerException) error).errorCode()).isEqualTo(ErrorCode.INVALID_URL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://b\u00fccher.example/", "https://\u0131ocalhost/", "https://example.com\u2024evil/"})
    void rejectsNonAsciiHostsSoCaseMappingCannotBypassChecks(String url) {
        assertThatThrownBy(() -> validator.normalize(url)).isInstanceOf(ShortenerException.class)
                .extracting(error -> ((ShortenerException) error).errorCode()).isEqualTo(ErrorCode.INVALID_URL);
    }

    @Test
    void acceptsPunycodeHosts() {
        assertThat(validator.normalize("https://XN--BCHER-KVA.example/")).isEqualTo("https://xn--bcher-kva.example/");
    }

    @Test
    void rejectsEmbeddedCredentials() {
        assertInvalid("https://user:secret@example.com/", "credentials");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost/", "http://LOCALHOST:8080/x", "http://api.localhost/",
            "https://printer.local/", "https://NAS.Local/share"})
    void rejectsLocalHostNames(String url) {
        assertInvalid(url, "publicly routable");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/", "http://127.10.20.30/",   // loopback
            "http://0.0.0.0/",                             // unspecified
            "http://10.0.0.1/", "http://10.255.255.255/",  // 10/8
            "http://172.16.0.1/", "http://172.31.255.254/", // 172.16/12
            "http://192.168.1.1/",                          // 192.168/16
            "http://169.254.169.254/latest/meta-data",      // link-local (cloud metadata)
            "http://[::1]/", "http://[::]/",                // IPv6 loopback and unspecified
            "http://[fc00::1]/", "http://[fd12:3456::1]/",  // fc00::/7
            "http://[fe80::1]/",                            // IPv6 link-local
            "http://[::ffff:127.0.0.1]/"                    // IPv4-mapped loopback
    })
    void rejectsNonPublicLiteralAddresses(String url) {
        assertInvalid(url, "publicly routable");
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://2130706433/", "http://0x7f000001/", "http://127.1/", "http://999.1.1.1/"})
    void rejectsAmbiguousNumericHostForms(String url) {
        assertThatThrownBy(() -> validator.normalize(url)).isInstanceOf(ShortenerException.class)
                .extracting(error -> ((ShortenerException) error).errorCode()).isEqualTo(ErrorCode.INVALID_URL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://172.15.0.1/", "http://172.32.0.1/", "http://8.8.8.8/", "http://[2001:4860::8888]/"})
    void acceptsPublicLiteralAddresses(String url) {
        assertThat(validator.normalize(url)).isEqualTo(url);
    }

    @ParameterizedTest
    @CsvSource({
            "HTTPS://Example.COM:443/Path?q=1#frag, https://example.com/Path?q=1#frag",
            "http://example.com:80, http://example.com/",
            "http://example.com:8080/a/../b, http://example.com:8080/b",
            "https://example.com/a%2Fb, https://example.com/a%2Fb"
    })
    void normalizesSchemeHostPortAndPath(String input, String expected) {
        assertThat(validator.normalize(input)).isEqualTo(expected);
    }

    private void assertInvalid(String url, String messageFragment) {
        assertThatThrownBy(() -> validator.normalize(url))
                .isInstanceOf(ShortenerException.class)
                .hasMessageContaining(messageFragment)
                .extracting(error -> ((ShortenerException) error).errorCode())
                .isEqualTo(ErrorCode.INVALID_URL);
    }
}
