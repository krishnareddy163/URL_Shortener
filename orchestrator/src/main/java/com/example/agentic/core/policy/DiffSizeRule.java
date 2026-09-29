package com.example.agentic.core.policy;

import com.example.agentic.core.workspace.Diff;

import java.util.Optional;

/** Escalates large changes, which are harder to review and riskier to promote automatically. */
public final class DiffSizeRule implements RiskRule {
    private final int threshold;

    public DiffSizeRule(int threshold) {
        this.threshold = threshold;
    }

    @Override
    public Optional<String> flag(Diff diff) {
        int changed = diff.changedLines();
        return changed > threshold
                ? Optional.of("DiffSizeRule: " + changed + " changed lines exceeds " + threshold)
                : Optional.empty();
    }
}
