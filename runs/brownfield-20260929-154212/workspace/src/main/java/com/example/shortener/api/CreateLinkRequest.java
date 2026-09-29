package com.example.shortener.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Body of {@code POST /api/v1/links}.
 *
 * @param url         destination URL (http/https, at most 2048 characters)
 * @param customAlias optional alias, 3 to 32 characters of {@code [A-Za-z0-9_-]}
 * @param expiresAt   optional future instant after which the link stops redirecting
 */
public record CreateLinkRequest(
        @NotBlank @Size(max = 2048) String url,
        String customAlias,
        Instant expiresAt) {
}
