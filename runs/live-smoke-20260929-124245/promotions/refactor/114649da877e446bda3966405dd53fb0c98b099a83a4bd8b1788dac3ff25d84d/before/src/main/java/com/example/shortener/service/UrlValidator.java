package com.example.shortener.service;

import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

/**
 * Validates and normalizes destination URLs.
 *
 * <p>Rules: http/https only, at most 2048 characters, host required, no embedded credentials, and no
 * {@code localhost}, {@code *.local}, or literal IPs in loopback, link-local, unspecified, or private
 * ranges. Host names are never resolved, so DNS rebinding is out of scope (documented limitation).
 */
@Component
public class UrlValidator {
    public static final int MAX_LENGTH = 2048;

    private static final Pattern DOTTED_QUAD = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern NUMERIC_LABEL = Pattern.compile("\\d+|0x[0-9a-f]*");

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
        if (isNonPublicHost(unbracket(host))) {
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

    private static boolean isNonPublicHost(String host) {
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local")) {
            return true;
        }
        if (host.contains(":")) {
            return isNonPublicIpv6(host);
        }
        if (DOTTED_QUAD.matcher(host).matches()) {
            return isNonPublicIpv4(host);
        }
        // Integer, hex, and shortened IPv4 forms (e.g. 2130706433, 0x7f000001) are addresses to clients.
        return isNumericHost(host);
    }

    /** True if every dot-separated label is decimal or 0x-hex (a trailing dot is allowed). */
    private static boolean isNumericHost(String host) {
        String trimmed = host.endsWith(".") ? host.substring(0, host.length() - 1) : host;
        if (trimmed.isEmpty()) {
            return false;
        }
        for (String label : trimmed.split("\\.", -1)) {
            if (!NUMERIC_LABEL.matcher(label).matches()) {
                return false;
            }
        }
        return true;
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

    private static boolean isNonPublicIpv4(String host) {
        String[] parts = host.split("\\.");
        int[] octets = new int[4];
        for (int index = 0; index < 4; index++) {
            octets[index] = Integer.parseInt(parts[index]);
            if (octets[index] > 255) {
                return true;
            }
        }
        return isNonPublicIpv4(octets[0], octets[1]);
    }

    private static boolean isNonPublicIpv4(int first, int second) {
        return first == 0
                || first == 10
                || first == 127
                || (first == 169 && second == 254)
                || (first == 172 && second >= 16 && second <= 31)
                || (first == 192 && second == 168);
    }

    private static boolean isNonPublicIpv6(String literal) {
        try {
            InetAddress address = InetAddress.ofLiteral(literal);
            if (address instanceof Inet4Address mapped) {
                byte[] bytes = mapped.getAddress();
                return isNonPublicIpv4(bytes[0] & 0xff, bytes[1] & 0xff);
            }
            byte[] bytes = address.getAddress();
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || (bytes[0] & 0xfe) == 0xfc;
        } catch (IllegalArgumentException _) {
            return true;
        }
    }

    private static String unbracket(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    private static ShortenerException invalid(String message) {
        return new ShortenerException(ErrorCode.INVALID_URL, message);
    }
}
