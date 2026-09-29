package com.example.shortener.domain;

import java.time.Instant;

/**
 * One audited state-changing request. It holds no personal data: {@code clientKey} is the same opaque hash the
 * rate limiter uses, and {@code path} excludes the query string.
 *
 * @param occurredAt when the request completed
 * @param clientKey  opaque client key
 * @param method     HTTP method
 * @param path       request path, at most {@value #MAX_PATH} characters
 * @param status     final HTTP status
 */
public record AuditEvent(Instant occurredAt, String clientKey, String method, String path, int status) {
    public static final int MAX_PATH = 256;

    public AuditEvent {
        path = path.length() > MAX_PATH ? path.substring(0, MAX_PATH) : path;
    }
}
