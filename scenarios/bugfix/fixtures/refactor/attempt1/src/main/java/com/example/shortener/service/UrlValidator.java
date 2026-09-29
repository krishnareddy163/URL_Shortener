package com.example.shortener.service;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Validates and normalizes destination URLs.
 *
 * <p>Rules: http/https only, at most 2048 characters, host required, no embedded credentials, and no
 * {@code localhost}, {@code *.local}, or literal IPs in loopback, link-local, unspecified, or private
 * ranges. Host classification lives in {@link HostClassifier}. Host names are never resolved, so DNS rebinding
 * is out of scope (documented limitation).
 */
@Component
public class UrlValidator {
    public static final int MAX_LENGTH = 2048;

    /** Returns the normalized URL (lower-case scheme and host, default port removed) or throws INVALID_URL. */
    public String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("URL is required");
        }
        if (value.length() > MAX_LENGTH) {
            throw invalid("URL must be at most " + MAX_LENGTH + " characters");
        }
        URI uri;
        try {
            uri = new URI(value.strip()).normalize();
        } catch (URISyntaxException _) {
            throw invalid("URL is malformed");
        }
        String scheme = uri.getScheme() == null ? "" : asciiLowerCase(uri.getScheme());
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw invalid("URL scheme must be http or https");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw invalid("URL must include a host");
        }
        if (uri.getRawUserInfo() != null) {
            throw invalid("URL must not contain credentials");
        }
        String host = asciiLowerCase(uri.getHost());
        if (HostClassifier.isNonPublic(unbracket(host))) {
            throw invalid("URL host must be publicly routable");
        }
        return rebuild(scheme, host, uri);
    }

    private static String rebuild(String scheme, String host, URI uri) {
        StringBuilder url = new StringBuilder(scheme).append("://").append(host);
        int port = uri.getPort();
        boolean defaultPort = (scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443);
        if (port != -1 && !defaultPort) {
            url.append(':').append(port);
        }
        url.append(uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath());
        if (uri.getRawQuery() != null) {
            url.append('?').append(uri.getRawQuery());
        }
        if (uri.getRawFragment() != null) {
            url.append('#').append(uri.getRawFragment());
        }
        return url.toString();
    }

    /**
     * Lower-cases ASCII letters only. Non-ASCII input is rejected, so Unicode case mapping can never turn a
     * look-alike host into {@code localhost}; internationalized names must be sent as punycode.
     */
    private static String asciiLowerCase(String value) {
        char[] chars = value.toCharArray();
        for (int index = 0; index < chars.length; index++) {
            char current = chars[index];
            if (current > 0x7f) {
                throw invalid("URL must be ASCII; send internationalized host names as punycode");
            }
            if (current >= 'A' && current <= 'Z') {
                chars[index] = (char) (current + 'a' - 'A');
            }
        }
        return new String(chars);
    }

    private static String unbracket(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    private static ShortenerException invalid(String message) {
        return new ShortenerException(ErrorCode.INVALID_URL, message);
    }
}
