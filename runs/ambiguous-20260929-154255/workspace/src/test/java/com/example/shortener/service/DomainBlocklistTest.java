package com.example.shortener.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DomainBlocklistTest {
    private final DomainBlocklist blocklist = new DomainBlocklist(" Malware.test, phishing.test ,");

    @Test
    void blocksListedDomainsAndTheirSubdomainsCaseInsensitively() {
        assertThat(blocklist.blocks("malware.test")).isTrue();
        assertThat(blocklist.blocks("cdn.MALWARE.test")).isTrue();
        assertThat(blocklist.blocks("phishing.test")).isTrue();
    }

    @Test
    void doesNotBlockLookalikesOrOtherDomains() {
        assertThat(blocklist.blocks("notmalware.test")).isFalse();
        assertThat(blocklist.blocks("malware.test.example.com")).isFalse();
        assertThat(blocklist.blocks("example.com")).isFalse();
        assertThat(DomainBlocklist.empty().blocks("malware.test")).isFalse();
    }

    @Test
    void validatorRejectsBlocklistedHostsAfterV1Checks() {
        UrlValidator validator = new UrlValidator(blocklist);
        assertThatThrownBy(() -> validator.normalize("https://login.phishing.test/account"))
                .isInstanceOf(ShortenerException.class).hasMessageContaining("blocklisted");
        assertThat(validator.normalize("http://example.com/ok")).isEqualTo("http://example.com/ok");
    }
}
