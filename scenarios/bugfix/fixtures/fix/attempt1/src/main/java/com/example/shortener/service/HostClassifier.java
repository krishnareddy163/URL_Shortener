package com.example.shortener.service;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.util.regex.Pattern;

/**
 * Decides whether a URL host is non-public: {@code localhost}, {@code *.localhost}, {@code *.local}, and literal
 * IPs in loopback, link-local, unspecified, or private ranges, including integer, hex, and shortened IPv4 forms.
 * Host names are never resolved.
 */
final class HostClassifier {
    private static final Pattern DOTTED_QUAD = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern NUMERIC_LABEL = Pattern.compile("\\d+|0x[0-9a-f]*");

    private HostClassifier() {
    }

    /** True if {@code host} (lower-case ASCII, IPv6 without brackets) must not be a redirect target. */
    static boolean isNonPublic(String host) {
        if (host.equals("localhost") || host.equals("localhost.") || host.endsWith(".localhost")
                || host.endsWith(".local")) {
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
}
