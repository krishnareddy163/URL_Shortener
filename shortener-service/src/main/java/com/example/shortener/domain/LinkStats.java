package com.example.shortener.domain;

import java.time.Instant;
import java.util.List;

/**
 * Aggregate click analytics for one link. Contains no personal data.
 *
 * @param code           short code
 * @param totalClicks    all recorded clicks
 * @param lastAccessedAt most recent click, or {@code null} if never clicked
 * @param clicksPerDay   per-UTC-day buckets in ascending date order
 */
public record LinkStats(String code, long totalClicks, Instant lastAccessedAt, List<DailyClicks> clicksPerDay) {

    public LinkStats {
        clicksPerDay = List.copyOf(clicksPerDay);
    }
}
