package com.example.shortener.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory token bucket per opaque client key (a hash, never a raw IP).
 *
 * <p>Each key may burst up to {@code requestsPerMinute} and refills continuously. Arithmetic is exact:
 * one token is {@value #UNITS_PER_TOKEN} units and each elapsed millisecond adds
 * {@code requestsPerMinute} units. Idle full buckets are evicted once the table exceeds
 * {@link #MAX_TRACKED_CLIENTS}, bounding memory under key churn.
 */
@Component
public final class RateLimiter {
    static final int MAX_TRACKED_CLIENTS = 100_000;
    private static final long UNITS_PER_TOKEN = 60_000;

    private final long unitsPerMilli;
    private final long capacityUnits;
    private final Clock clock;
    private final Map<String, Bucket> buckets = new HashMap<>();

    public RateLimiter(@Value("${shortener.rate-limit.requests-per-minute:20}") int requestsPerMinute, Clock clock) {
        if (requestsPerMinute <= 0) {
            throw new IllegalArgumentException("requestsPerMinute must be positive");
        }
        this.unitsPerMilli = requestsPerMinute;
        this.capacityUnits = requestsPerMinute * UNITS_PER_TOKEN;
        this.clock = clock;
    }

    /** Consumes one token for the key; returns {@code false} when the key is rate limited. */
    public synchronized boolean tryAcquire(String clientKey) {
        long now = clock.millis();
        if (buckets.size() >= MAX_TRACKED_CLIENTS && !buckets.containsKey(clientKey)) {
            buckets.values().removeIf(bucket -> refill(bucket, now) >= capacityUnits);
        }
        Bucket bucket = buckets.computeIfAbsent(clientKey, key -> new Bucket(capacityUnits, now));
        if (refill(bucket, now) < UNITS_PER_TOKEN) {
            return false;
        }
        bucket.units -= UNITS_PER_TOKEN;
        return true;
    }

    private long refill(Bucket bucket, long now) {
        long elapsed = Math.max(0, now - bucket.updatedAtMillis);
        long added = elapsed >= capacityUnits / unitsPerMilli ? capacityUnits : elapsed * unitsPerMilli;
        bucket.units = Math.min(capacityUnits, bucket.units + added);
        bucket.updatedAtMillis = now;
        return bucket.units;
    }

    private static final class Bucket {
        private long units;
        private long updatedAtMillis;

        private Bucket(long units, long updatedAtMillis) {
            this.units = units;
            this.updatedAtMillis = updatedAtMillis;
        }
    }
}
