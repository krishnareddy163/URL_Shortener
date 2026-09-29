package com.example.shortener.api;

import com.example.shortener.domain.ShortLink;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Representation of a short link returned by the create endpoint.
 *
 * @param code      short code
 * @param shortUrl  absolute URL that redirects to {@code url}
 * @param url       normalized destination URL
 * @param createdAt creation instant (UTC, ISO-8601)
 * @param expiresAt expiry instant, omitted when the link never expires
 */
public record LinkResponse(String code, String shortUrl, String url, Instant createdAt,
                           @JsonInclude(JsonInclude.Include.NON_NULL) Instant expiresAt) {

    static LinkResponse of(ShortLink link, String baseUrl) {
        return new LinkResponse(link.code(), baseUrl + "/" + link.code(), link.url(), link.createdAt(), link.expiresAt());
    }
}
