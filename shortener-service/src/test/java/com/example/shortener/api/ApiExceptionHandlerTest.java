package com.example.shortener.api;

import com.example.shortener.service.ErrorCode;
import com.example.shortener.service.IncidentNotificationService;
import com.example.shortener.service.IncidentSeverity;
import com.example.shortener.service.ShortenerException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ApiExceptionHandlerTest {

    // Disabled notification service — isolates handler logic from email sending in most tests.
    private static final IncidentNotificationService NO_OP =
            new IncidentNotificationService(Optional.empty(), false, "", "");

    private final ApiExceptionHandler handler = new ApiExceptionHandler(NO_OP);

    @Test
    void businessErrorsMapToTheirStatus() {
        assertThat(ApiExceptionHandler.statusFor(ErrorCode.INVALID_URL)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ApiExceptionHandler.statusFor(ErrorCode.INVALID_ALIAS)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ApiExceptionHandler.statusFor(ErrorCode.ALIAS_TAKEN)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ApiExceptionHandler.statusFor(ErrorCode.URL_ALREADY_SHORTENED)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ApiExceptionHandler.statusFor(ErrorCode.LINK_NOT_FOUND)).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ApiExceptionHandler.statusFor(ErrorCode.RATE_LIMITED)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ApiExceptionHandler.statusFor(ErrorCode.CODE_GENERATION_FAILED)).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void codeGenerationFailureIsA500WithItsCode() {
        ResponseEntity<ErrorResponse> response = handler.business(
                new ShortenerException(ErrorCode.CODE_GENERATION_FAILED, "Could not allocate a unique short code"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(ErrorResponse.of("CODE_GENERATION_FAILED", "Could not allocate a unique short code"));
    }

    @Test
    void nonCriticalBusinessErrorsDoNotLogAtErrorLevel() {
        // P4 errors (INVALID_URL) take the non-P1 branch in business() — no error log, just a response
        ResponseEntity<ErrorResponse> response = handler.business(
                new ShortenerException(ErrorCode.INVALID_URL, "Not a valid URL"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(ErrorResponse.of("INVALID_URL", "Not a valid URL"));
    }

    @Test
    void unexpectedFailuresHideTheirDetails() {
        ResponseEntity<ErrorResponse> response = handler.unexpected(new IllegalStateException("secret internal detail"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isEqualTo(ErrorResponse.of("INTERNAL_ERROR", "The request could not be completed"));
    }

    @Test
    void wrongMediaTypeIs415() {
        ResponseEntity<ErrorResponse> response = handler.wrongMediaType();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        assertThat(response.getBody()).isEqualTo(ErrorResponse.of("UNSUPPORTED_MEDIA_TYPE", "Content type must be application/json"));
    }

    @Test
    void businessErrorTriggersNotification() {
        IncidentNotificationService notifier = mock(IncidentNotificationService.class);
        ApiExceptionHandler h = new ApiExceptionHandler(notifier);

        h.business(new ShortenerException(ErrorCode.INVALID_URL, "bad url"));

        verify(notifier).notify(IncidentSeverity.P4_LOW, "INVALID_URL", null);
    }

    @Test
    void unexpectedErrorTriggersP1Notification() {
        IncidentNotificationService notifier = mock(IncidentNotificationService.class);
        ApiExceptionHandler h = new ApiExceptionHandler(notifier);

        h.unexpected(new IllegalStateException("boom"));

        verify(notifier).notify(IncidentSeverity.P1_CRITICAL, "IllegalStateException", null);
    }
}
