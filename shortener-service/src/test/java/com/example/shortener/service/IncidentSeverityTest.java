package com.example.shortener.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentSeverityTest {

    @Test
    void errorCodesMapToExpectedSeverities() {
        assertThat(IncidentSeverity.forErrorCode(ErrorCode.CODE_GENERATION_FAILED)).isEqualTo(IncidentSeverity.P1_CRITICAL);
        assertThat(IncidentSeverity.forErrorCode(ErrorCode.RATE_LIMITED)).isEqualTo(IncidentSeverity.P2_HIGH);
        assertThat(IncidentSeverity.forErrorCode(ErrorCode.ALIAS_TAKEN)).isEqualTo(IncidentSeverity.P3_MEDIUM);
        assertThat(IncidentSeverity.forErrorCode(ErrorCode.URL_ALREADY_SHORTENED)).isEqualTo(IncidentSeverity.P3_MEDIUM);
        assertThat(IncidentSeverity.forErrorCode(ErrorCode.LINK_NOT_FOUND)).isEqualTo(IncidentSeverity.P3_MEDIUM);
        assertThat(IncidentSeverity.forErrorCode(ErrorCode.INVALID_URL)).isEqualTo(IncidentSeverity.P4_LOW);
        assertThat(IncidentSeverity.forErrorCode(ErrorCode.INVALID_ALIAS)).isEqualTo(IncidentSeverity.P4_LOW);
    }

    @Test
    void unhandledNonBusinessExceptionIsAlwaysP1() {
        assertThat(IncidentSeverity.forUnhandledException(new RuntimeException("unexpected")))
                .isEqualTo(IncidentSeverity.P1_CRITICAL);
    }

    @Test
    void unhandledShortenerExceptionDelegatesToItsErrorCode() {
        assertThat(IncidentSeverity.forUnhandledException(
                new ShortenerException(ErrorCode.RATE_LIMITED, "too fast")))
                .isEqualTo(IncidentSeverity.P2_HIGH);
    }

    @Test
    void eachSeverityHasADescription() {
        assertThat(IncidentSeverity.P1_CRITICAL.description()).contains("on-call");
        assertThat(IncidentSeverity.P2_HIGH.description()).contains("1 hour");
        assertThat(IncidentSeverity.P3_MEDIUM.description()).contains("business day");
        assertThat(IncidentSeverity.P4_LOW.description()).contains("backlog");
    }
}
