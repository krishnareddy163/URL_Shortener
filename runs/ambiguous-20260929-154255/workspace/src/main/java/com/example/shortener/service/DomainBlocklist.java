package com.example.shortener.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Operator-configured destination blocklist ({@code shortener.blocklist.domains}, comma-separated). A host is
 * blocked if it equals a listed domain or is a subdomain of one; matching is case-insensitive.
 */
@Component
public class DomainBlocklist {
    private final Set<String> domains;

    @Autowired
    public DomainBlocklist(@Value("${shortener.blocklist.domains:}") String configured) {
        this(Arrays.stream(configured.split(","))
                .map(domain -> domain.strip().toLowerCase(Locale.ROOT))
                .filter(domain -> !domain.isEmpty())
                .collect(Collectors.toUnmodifiableSet()));
    }

    DomainBlocklist(Set<String> domains) {
        this.domains = Set.copyOf(domains);
    }

    /** A blocklist that blocks nothing. */
    public static DomainBlocklist empty() {
        return new DomainBlocklist(Set.of());
    }

    public boolean blocks(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return domains.stream().anyMatch(domain -> normalized.equals(domain) || normalized.endsWith("." + domain));
    }
}
