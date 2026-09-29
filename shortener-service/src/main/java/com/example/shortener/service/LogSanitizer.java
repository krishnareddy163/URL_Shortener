package com.example.shortener.service;

/** Neutralizes CR/LF in values written to logs so request-derived text cannot forge log lines. */
public final class LogSanitizer {

    private LogSanitizer() {
    }

    public static String clean(Object value) {
        return String.valueOf(value).replace('\r', '_').replace('\n', '_');
    }
}
