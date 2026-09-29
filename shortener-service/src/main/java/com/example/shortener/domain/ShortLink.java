package com.example.shortener.domain;

import java.time.Instant;

/**
 * A persisted short link.
 *
 * @param code      unique short code or custom alias
 * @param url       normalized destination URL
 * @param urlHash   SHA-256 of the normalized URL, used for idempotent creation
 * @param createdAt creation instant (UTC)
 */
public record ShortLink(String code, String url, String urlHash, Instant createdAt) {
}
