package com.example.agentic.core.gate;

import com.example.agentic.core.state.Artifact;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Review exit gate: proves that all submitted code was reviewed and every issue has a recorded outcome.
 *
 * <ul>
 *   <li>{@code reviewedFiles} must list every file of every upstream artifact (the node's transitive
 *       dependencies), so a reviewer cannot skip part of the change.</li>
 *   <li>Each finding needs {@code severity} (LOW, MEDIUM, HIGH, CRITICAL), {@code file}, {@code message},
 *       {@code status} (FIXED, ACCEPTED, DEFERRED) and a non-blank {@code resolution}.</li>
 *   <li>{@code recommendation} is GO or NO_GO, and a HIGH or CRITICAL finding that is not FIXED forces NO_GO.</li>
 * </ul>
 */
public final class ReviewCompleteGate implements Gate {
    public static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    public static final Set<String> STATUSES = Set.of("FIXED", "ACCEPTED", "DEFERRED");
    private static final Set<String> BLOCKING = Set.of("HIGH", "CRITICAL");
    private static final int LISTED = 10;

    @Override
    public String id() {
        return "review-complete";
    }

    @Override
    public GateResult evaluate(GateContext context) {
        if (context.isEntry()) {
            return GateResult.fail(id(), "review-complete is an exit gate and needs a proposal");
        }
        Map<String, Object> data = context.proposal().data();
        return problem(data, submittedFiles(context.upstream().values()))
                .map(reason -> GateResult.fail(id(), reason)).orElseGet(GateResult::pass);
    }

    /** Every file the given artifacts submitted, sorted. */
    public static Set<String> submittedFiles(Collection<Artifact> artifacts) {
        Set<String> files = new TreeSet<>();
        artifacts.forEach(artifact -> files.addAll(artifact.files().keySet()));
        return files;
    }

    private static Optional<String> problem(Map<String, Object> data, Set<String> submitted) {
        if (!(data.get("reviewedFiles") instanceof List<?> reviewed)) {
            return Optional.of("reviewedFiles must list every submitted file the review covered");
        }
        Set<String> missing = new TreeSet<>(submitted);
        reviewed.forEach(missing::remove);
        if (!missing.isEmpty()) {
            return Optional.of(missing.size() + " submitted file(s) not reviewed: "
                    + String.join(", ", missing.stream().limit(LISTED).toList()) + (missing.size() > LISTED ? ", ..." : ""));
        }
        if (!(data.get("findings") instanceof List<?> findings)) {
            return Optional.of("findings must be a list (empty when there are none)");
        }
        boolean unfixedBlocking = false;
        for (Object item : findings) {
            Optional<String> findingProblem = findingProblem(item);
            if (findingProblem.isPresent()) {
                return findingProblem;
            }
            Map<?, ?> finding = (Map<?, ?>) item;
            unfixedBlocking |= BLOCKING.contains(finding.get("severity")) && !"FIXED".equals(finding.get("status"));
        }
        Object recommendation = data.get("recommendation");
        if (!"GO".equals(recommendation) && !"NO_GO".equals(recommendation)) {
            return Optional.of("recommendation must be GO or NO_GO");
        }
        if (unfixedBlocking && "GO".equals(recommendation)) {
            return Optional.of("a HIGH or CRITICAL finding that is not FIXED requires NO_GO");
        }
        return Optional.empty();
    }

    private static Optional<String> findingProblem(Object item) {
        if (!(item instanceof Map<?, ?> finding) || !(finding.get("file") instanceof String)
                || !(finding.get("message") instanceof String message) || message.isBlank()) {
            return Optional.of("each finding needs file and message");
        }
        if (!(finding.get("severity") instanceof String severity) || !SEVERITIES.contains(severity)) {
            return Optional.of("finding severity must be one of " + new TreeSet<>(SEVERITIES));
        }
        if (!(finding.get("status") instanceof String status) || !STATUSES.contains(status)) {
            return Optional.of("finding status must be one of " + new TreeSet<>(STATUSES) + ": " + message);
        }
        if (!(finding.get("resolution") instanceof String resolution) || resolution.isBlank()) {
            return Optional.of("finding needs a resolution: " + message);
        }
        return Optional.empty();
    }
}
