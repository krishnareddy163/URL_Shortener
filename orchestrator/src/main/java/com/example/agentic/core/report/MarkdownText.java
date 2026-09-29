package com.example.agentic.core.report;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** Text helpers for Markdown tables. */
final class MarkdownText {
    static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneOffset.UTC);
    private static final int CELL_LIMIT = 240;

    private MarkdownText() {
    }

    /** Flattens whitespace, escapes pipes and truncates, so any text fits in one table cell. */
    static String cell(String text) {
        return cell(text, CELL_LIMIT);
    }

    static String cell(String text, int limit) {
        if (text == null) {
            return "";
        }
        String flat = text.replaceAll("\\s+", " ").replace("|", "\\|").strip();
        return flat.length() > limit ? flat.substring(0, limit - 3) + "..." : flat;
    }

    static String firstLine(String text) {
        return firstLines(text, 1);
    }

    /** The first {@code count} lines joined with a visible line-break marker. */
    static String firstLines(String text, int count) {
        return text == null ? "" : String.join(" ⏎ ", text.lines().limit(count).toList());
    }
}
