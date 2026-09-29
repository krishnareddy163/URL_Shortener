package com.example.agentic.core.policy;

import com.example.agentic.core.workspace.Diff;

import java.util.List;
import java.util.Optional;

/**
 * Classifies a diff as high-impact. Any flag escalates an {@code ESCALATE_ON_RISK} node to human approval.
 */
public interface RiskRule {

    /** Returns the escalation reason, or empty if the diff is not risky under this rule. */
    Optional<String> flag(Diff diff);

    /** The default rule set configured from policies.yaml. */
    static List<RiskRule> defaults(PolicyConfig policy) {
        return List.of(
                new MigrationPathRule(policy.risk().migrationGlobs()),
                new PomChangeRule(),
                new DiffSizeRule(policy.risk().diffSizeThreshold()),
                new DeletionRule());
    }
}
