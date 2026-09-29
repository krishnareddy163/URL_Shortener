package com.example.agentic.core.gate;

import com.example.agentic.core.state.Hashing;

import java.util.regex.Pattern;

/**
 * Stable fingerprint of a failure: SHA-256 of the gate id plus the failure text with volatile parts
 * (absolute paths, timestamps, line/column numbers, durations, hex ids) removed. Two attempts failing
 * "the same way" produce the same signature, which is what the circuit breaker compares.
 */
public final class FailureSignature {
    private static final Pattern[] VOLATILE = {
            Pattern.compile("(?:[A-Za-z]:)?/[\\w.@+-]++/[\\w.@+/-]*+"),
            Pattern.compile("\\d{4}-\\d{2}-\\d{2}[T ][\\d:.,]++(?:Z|[+-][\\d:]{4,5})?"),
            Pattern.compile("\\b\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d+)?\\b"),
            Pattern.compile("(?i)time elapsed:?\\s*[\\d.,]+\\s*s(?:ec)?"),
            Pattern.compile("(?i)\\b\\d+(?:[.,]\\d+)?\\s*(?:ms|s|sec|seconds)\\b"),
            Pattern.compile(":\\[?\\d+(?:,\\d+)?\\]?(?::\\d+)?"),
            Pattern.compile("(?i)\\bline \\d+(?:, column \\d+)?"),
            Pattern.compile("\\b[0-9a-f]{12,}\\b"),
            Pattern.compile("@[0-9a-f]{4,}\\b"),
    };

    private FailureSignature() {
    }

    public static String of(String gateId, String failureText) {
        return Hashing.sha256(gateId + "\n" + normalize(failureText));
    }

    static String normalize(String text) {
        String normalized = text == null ? "" : text;
        for (Pattern pattern : VOLATILE) {
            normalized = pattern.matcher(normalized).replaceAll("#");
        }
        return normalized.replaceAll("\\s+", " ").trim();
    }
}
