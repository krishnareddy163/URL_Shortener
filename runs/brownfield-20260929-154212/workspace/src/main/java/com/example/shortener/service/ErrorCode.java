package com.example.shortener.service;

/** Stable, client-visible error codes raised by the service layer. */
public enum ErrorCode {
    INVALID_URL,
    INVALID_ALIAS,
    INVALID_EXPIRY,
    ALIAS_TAKEN,
    URL_ALREADY_SHORTENED,
    LINK_NOT_FOUND,
    LINK_EXPIRED,
    RATE_LIMITED,
    CODE_GENERATION_FAILED
}
