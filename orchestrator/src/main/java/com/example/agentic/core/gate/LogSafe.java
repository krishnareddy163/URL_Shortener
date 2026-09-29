package com.example.agentic.core.gate;

/** Neutralizes CR/LF in values written to logs so untrusted text cannot forge log lines. */
public final class LogSafe {

    private LogSafe() {
    }

    public static String clean(Object value) {
        return String.valueOf(value).replace('\r', '_').replace('\n', '_');
    }
}
