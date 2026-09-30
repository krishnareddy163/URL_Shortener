package com.example.shortener.service;

/**
 * Operational priority for errors observed at runtime.
 *
 * <ul>
 *   <li>P1 — service degraded; page on-call immediately</li>
 *   <li>P2 — significant error pattern; investigate within 1 hour</li>
 *   <li>P3 — business-rule violation; investigate within 1 business day</li>
 *   <li>P4 — client error; fix in backlog</li>
 * </ul>
 *
 * Use {@link #forErrorCode} for known business errors and {@link #forUnhandledException} for
 * anything that reached the catch-all handler.
 */
public enum IncidentSeverity {
    P1_CRITICAL("Page on-call immediately"),
    P2_HIGH("Investigate within 1 hour"),
    P3_MEDIUM("Investigate within 1 business day"),
    P4_LOW("Fix in backlog");

    private final String description;

    IncidentSeverity(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** Maps a known business error code to its operational priority. */
    public static IncidentSeverity forErrorCode(ErrorCode code) {
        return switch (code) {
            case CODE_GENERATION_FAILED -> P1_CRITICAL;
            case RATE_LIMITED -> P2_HIGH;
            case ALIAS_TAKEN, URL_ALREADY_SHORTENED, LINK_NOT_FOUND -> P3_MEDIUM;
            case INVALID_URL, INVALID_ALIAS -> P4_LOW;
        };
    }

    /**
     * Classifies an exception that reached the catch-all handler. A {@link ShortenerException}
     * delegates to {@link #forErrorCode}; everything else is P1 because it means an unhandled code path.
     */
    public static IncidentSeverity forUnhandledException(Exception exception) {
        if (exception instanceof ShortenerException se) {
            return forErrorCode(se.errorCode());
        }
        return P1_CRITICAL;
    }
}
