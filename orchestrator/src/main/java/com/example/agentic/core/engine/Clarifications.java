package com.example.agentic.core.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Handles {@code data.ambiguities} of a requirements proposal: which blocking questions are unanswered,
 * and how answers and assumptions are folded into the final artifact (so a changed answer changes its hash).
 */
public final class Clarifications {

    private Clarifications() {
    }

    public static List<Map<?, ?>> ambiguities(Map<String, Object> data) {
        List<Map<?, ?>> result = new ArrayList<>();
        if (data.get("ambiguities") instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> ambiguity) {
                    result.add(ambiguity);
                }
            }
        }
        return result;
    }

    public static List<Map<?, ?>> unansweredBlocking(Map<String, Object> data, Map<String, String> answers) {
        return ambiguities(data).stream()
                .filter(ambiguity -> Boolean.TRUE.equals(ambiguity.get("blocking")))
                .filter(ambiguity -> !answers.containsKey(String.valueOf(ambiguity.get("id"))))
                .toList();
    }

    /**
     * Returns {@code data} plus {@code answers} (for this proposal's questions) and assumptions extended
     * with the fallback assumption of every unanswered non-blocking question.
     */
    public static Map<String, Object> fold(Map<String, Object> data, Map<String, String> answers) {
        List<Map<?, ?>> ambiguities = ambiguities(data);
        if (ambiguities.isEmpty()) {
            return data;
        }
        Map<String, String> relevant = new TreeMap<>();
        List<Object> assumptions = new ArrayList<>();
        if (data.get("assumptions") instanceof List<?> existing) {
            assumptions.addAll(existing);
        }
        for (Map<?, ?> ambiguity : ambiguities) {
            String id = String.valueOf(ambiguity.get("id"));
            if (answers.containsKey(id)) {
                relevant.put(id, answers.get(id));
            } else if (!Boolean.TRUE.equals(ambiguity.get("blocking")) && ambiguity.get("assumptionIfUnanswered") != null) {
                assumptions.add(id + ": " + ambiguity.get("assumptionIfUnanswered"));
            }
        }
        Map<String, Object> folded = new LinkedHashMap<>(data);
        folded.put("answers", relevant);
        folded.put("assumptions", assumptions);
        return folded;
    }

    /** Fixture variant key: the slugified answer to {@code questionId}, or {@code default}. */
    public static String variantKey(String questionId, Map<String, String> answers) {
        if (questionId == null || !answers.containsKey(questionId)) {
            return "default";
        }
        String slug = trimDashes(answers.get(questionId).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-"));
        return slug.isEmpty() ? "default" : slug;
    }

    private static String trimDashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '-') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(start, end);
    }
}
