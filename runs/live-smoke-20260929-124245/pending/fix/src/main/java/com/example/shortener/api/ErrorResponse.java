package com.example.shortener.api;

/**
 * Error envelope for every non-2xx/3xx response: {@code {"error":{"code":"...","message":"..."}}}.
 *
 * @param error the error detail
 */
public record ErrorResponse(Detail error) {

    static ErrorResponse of(String code, String message) {
        return new ErrorResponse(new Detail(code, message));
    }

    /**
     * Error detail.
     *
     * @param code    UPPER_SNAKE error code
     * @param message human-readable, client-safe message
     */
    public record Detail(String code, String message) {
    }
}
