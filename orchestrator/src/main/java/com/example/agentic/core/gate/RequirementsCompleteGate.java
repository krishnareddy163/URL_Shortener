package com.example.agentic.core.gate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Quality gate for normalized requirements: a problem statement, at least one user story in the form
 * "As a ..., I want ..., so that ..." ("As an" and "As the" also work), at least one acceptance criterion, an
 * ambiguities list (entries need {@code id}, {@code question}, {@code blocking}; blocking ones need options,
 * non-blocking ones an assumption) and an assumptions list.
 */
public final class RequirementsCompleteGate implements Gate {
    private static final String BLOCKING = "blocking";
    /** "As a role, I want capability, so that benefit" (also "As an" and "As the"). */
    public static final Pattern USER_STORY = Pattern.compile("(?is)as (?:an?|the) \\S.*?, i want \\S.*?, so that \\S.*");

    @Override
    public String id() {
        return "requirements-complete";
    }

    @Override
    public GateResult evaluate(GateContext context) {
        if (context.isEntry()) {
            return GateResult.fail(id(), "requirements-complete is an exit gate and needs a proposal");
        }
        Map<String, Object> data = context.proposal().data();
        Optional<String> problem = structureProblem(data);
        if (problem.isEmpty() && data.get("ambiguities") instanceof List<?> ambiguities) {
            problem = ambiguities.stream().map(RequirementsCompleteGate::ambiguityProblem)
                    .flatMap(Optional::stream).findFirst();
        }
        return problem.map(reason -> GateResult.fail(id(), reason)).orElseGet(GateResult::pass);
    }

    private static Optional<String> structureProblem(Map<String, Object> data) {
        if (!(data.get("problemStatement") instanceof String statement) || statement.isBlank()) {
            return Optional.of("missing problemStatement");
        }
        if (!(data.get("userStories") instanceof List<?> stories) || stories.isEmpty()) {
            return Optional.of("userStories must be a non-empty list");
        }
        for (Object story : stories) {
            if (!(story instanceof String text) || !USER_STORY.matcher(text.strip()).matches()) {
                return Optional.of("user story must read \"As a <role>, I want <capability>, so that <benefit>\": " + story);
            }
        }
        if (!(data.get("acceptanceCriteria") instanceof List<?> criteria) || criteria.isEmpty()) {
            return Optional.of("acceptanceCriteria must be a non-empty list");
        }
        if (!(data.get("assumptions") instanceof List<?>)) {
            return Optional.of("assumptions must be a list");
        }
        if (!(data.get("ambiguities") instanceof List<?>)) {
            return Optional.of("ambiguities must be a list");
        }
        return Optional.empty();
    }

    private static Optional<String> ambiguityProblem(Object item) {
        if (!(item instanceof Map<?, ?> ambiguity) || !(ambiguity.get("id") instanceof String)
                || !(ambiguity.get("question") instanceof String) || !(ambiguity.get(BLOCKING) instanceof Boolean)) {
            return Optional.of("each ambiguity needs id, question and blocking");
        }
        boolean blocking = Boolean.TRUE.equals(ambiguity.get(BLOCKING));
        if (blocking && !(ambiguity.get("options") instanceof List<?> options && !options.isEmpty())) {
            return Optional.of("blocking ambiguity " + ambiguity.get("id") + " needs options");
        }
        if (!blocking && !(ambiguity.get("assumptionIfUnanswered") instanceof String)) {
            return Optional.of("non-blocking ambiguity " + ambiguity.get("id") + " needs assumptionIfUnanswered");
        }
        return Optional.empty();
    }
}
