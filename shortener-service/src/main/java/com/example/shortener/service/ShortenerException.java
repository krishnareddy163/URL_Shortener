package com.example.shortener.service;

/** Expected business failure carrying a stable {@link ErrorCode} and a client-safe message. */
public class ShortenerException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public ShortenerException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }
}
