package com.example.shortener.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Edge cases of address classification. Accepted URLs use https so they hold under an https-only policy too. */
class UrlValidatorEdgeCaseTest {
    private final UrlValidator validator = new UrlValidator();

    @Test
    void aMissingPathBecomesSlash() {
        assertThat(validator.normalize("https://example.com")).isEqualTo("https://example.com/");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://0.1.2.3/", "https://10.1.2.3/", "https://127.1.2.3/", "https://169.254.1.1/",
            "https://172.16.0.1/", "https://172.31.255.255/", "https://192.168.1.1/", "https://[fec0::1]/",
            "https://[fd12::1]/", "https://[fe80::1]/", "https://[::]/", "https://[::ffff:10.0.0.1]/",
            "https://[fe80::1%25en0]/"})
    void nonPublicAddressesAreRejected(String url) {
        assertThatThrownBy(() -> validator.normalize(url)).isInstanceOf(ShortenerException.class)
                .hasMessageContaining("publicly routable");
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://8.8.8.8/", "https://169.1.1.1/", "https://172.15.0.1/", "https://172.32.0.1/",
            "https://192.1.1.1/", "https://[2001:db8::1]/", "https://[::ffff:8.8.8.8]/", "https://example.com./"})
    void publicAddressesAreAccepted(String url) {
        assertThat(validator.normalize(url)).isEqualTo(url);
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://exämple.com/", "https://256.1.1.1/", "https://8.8.8.8./", "https://./"})
    void hostsTheUriParserCannotReadAreRejected(String url) {
        assertThatThrownBy(() -> validator.normalize(url)).isInstanceOf(ShortenerException.class)
                .hasMessageContaining("must include a host");
    }
}
