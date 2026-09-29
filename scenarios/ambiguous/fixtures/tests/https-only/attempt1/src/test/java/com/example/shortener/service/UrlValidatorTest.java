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
    @ValueSource(strings = {"https:///missing-host", "https://"})
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

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/", "HTTP://EXAMPLE.COM/docs", "http://8.8.8.8/"})
    void rejectsPlainHttpUnderTheHttpsOnlyPolicy(String url) {
        assertInvalid(url, "must be https");
    }

    @Test
    void rejectsEmbeddedCredentials() {
        assertInvalid("https://user:secret@example.com/", "credentials");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://localhost/", "https://LOCALHOST:8080/x", "https://api.localhost/",
            "https://printer.local/", "https://NAS.Local/share"})
    void rejectsLocalHostNames(String url) {
        assertInvalid(url, "publicly routable");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://127.0.0.1/", "https://127.10.20.30/",   // loopback
            "https://0.0.0.0/",                             // unspecified
            "https://10.0.0.1/", "https://10.255.255.255/",  // 10/8
            "https://172.16.0.1/", "https://172.31.255.254/", // 172.16/12
            "https://192.168.1.1/",                          // 192.168/16
            "https://169.254.169.254/latest/meta-data",      // link-local (cloud metadata)
            "https://[::1]/", "https://[::]/",                // IPv6 loopback and unspecified
            "https://[fc00::1]/", "https://[fd12:3456::1]/",  // fc00::/7
            "https://[fe80::1]/",                            // IPv6 link-local
            "https://[::ffff:127.0.0.1]/"                    // IPv4-mapped loopback
    })
    void rejectsNonPublicLiteralAddresses(String url) {
        assertInvalid(url, "publicly routable");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://2130706433/", "https://0x7f000001/", "https://127.1/", "https://999.1.1.1/"})
    void rejectsAmbiguousNumericHostForms(String url) {
        assertThatThrownBy(() -> validator.normalize(url)).isInstanceOf(ShortenerException.class)
                .extracting(error -> ((ShortenerException) error).errorCode()).isEqualTo(ErrorCode.INVALID_URL);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://172.15.0.1/", "https://172.32.0.1/", "https://8.8.8.8/", "https://[2001:4860::8888]/"})
    void acceptsPublicLiteralAddresses(String url) {
        assertThat(validator.normalize(url)).isEqualTo(url);
    }

    @ParameterizedTest
    @CsvSource({
            "HTTPS://Example.COM:443/Path?q=1#frag, https://example.com/Path?q=1#frag",
            "https://example.com:443, https://example.com/",
            "https://example.com:8080/a/../b, https://example.com:8080/b",
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
