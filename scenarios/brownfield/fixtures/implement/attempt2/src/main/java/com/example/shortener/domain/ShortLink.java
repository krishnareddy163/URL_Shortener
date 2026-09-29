package com.example.shortener.domain;

import java.time.Instant;

/**
 * A persisted short link.
 *
 * @param code      unique short code or custom alias
 * @param url       normalized destination URL
 * @param urlHash   SHA-256 of the normalized URL, used for idempotent creation
 * @param createdAt creation instant (UTC)
 * @param expiresAt instant after which redirects are refused, or {@code null} if the link never expires
 */
public record ShortLink(String code, String url, String urlHash, Instant createdAt, Instant expiresAt) {

    /** A link that never expires (v1 behavior). */
    public ShortLink(String code, String url, String urlHash, Instant createdAt) {
        this(code, url, urlHash, createdAt, null);
    }

    /** Whether the link is expired at {@code now}; a link without {@code expiresAt} never expires. */
    public boolean isExpiredAt(Instant now) {
        return expiresAt != null && !now.isBefore(expiresAt);
    }
}
