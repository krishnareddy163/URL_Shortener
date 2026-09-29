package com.example.shortener.domain;

import java.time.LocalDate;

/**
 * Click count for one UTC calendar day.
 *
 * @param date  UTC date
 * @param count clicks recorded on that date
 */
public record DailyClicks(LocalDate date, long count) {
}
