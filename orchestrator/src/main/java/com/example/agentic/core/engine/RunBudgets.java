package com.example.agentic.core.engine;

import com.example.agentic.core.state.RunState;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Global run budgets (R6). Wall-clock time counts only active segments, so time spent waiting for a human
 * is excluded. Exceeding any budget triggers safe-stop.
 *
 * @param maxTotalAttempts    agent calls plus human rejections
 * @param maxWallClockSeconds active execution time
 * @param maxAgentCalls       agent invocations
 */
public record RunBudgets(int maxTotalAttempts, int maxWallClockSeconds, int maxAgentCalls) {

    public static final RunBudgets DEFAULTS = new RunBudgets(25, 900, 60);

    /** Policy defaults overridden by workflow-specific values. */
    public static RunBudgets resolve(Map<String, Integer> policyDefaults, Map<String, Integer> workflowOverrides) {
        Map<String, Integer> merged = new HashMap<>(policyDefaults);
        merged.putAll(workflowOverrides);
        return new RunBudgets(
                merged.getOrDefault("maxTotalAttempts", DEFAULTS.maxTotalAttempts),
                merged.getOrDefault("maxWallClockSeconds", DEFAULTS.maxWallClockSeconds),
                merged.getOrDefault("maxAgentCalls", DEFAULTS.maxAgentCalls));
    }

    /** Reason the next agent call would exceed a budget, if any. */
    public Optional<String> exceeded(RunState state, Instant now) {
        if (state.totalAttempts() >= maxTotalAttempts) {
            return Optional.of("budget exhausted: maxTotalAttempts=" + maxTotalAttempts);
        }
        if (state.agentCalls() >= maxAgentCalls) {
            return Optional.of("budget exhausted: maxAgentCalls=" + maxAgentCalls);
        }
        Duration active = state.activeTime(now);
        if (active.toSeconds() >= maxWallClockSeconds) {
            return Optional.of("budget exhausted: maxWallClockSeconds=" + maxWallClockSeconds
                    + " (active " + active.toSeconds() + " s)");
        }
        return Optional.empty();
    }
}
