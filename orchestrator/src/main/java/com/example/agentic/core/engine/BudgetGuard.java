package com.example.agentic.core.engine;

import com.example.agentic.core.state.RunState;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

/** Checks the run budgets: policy defaults, overridden by the workflow's own budgets, against folded state. */
public final class BudgetGuard {
    private final Map<String, Integer> policyDefaults;
    private final Clock clock;

    public BudgetGuard(Map<String, Integer> policyDefaults, Clock clock) {
        this.policyDefaults = Map.copyOf(policyDefaults);
        this.clock = clock;
    }

    /** Why the next agent call would exceed a budget, if it would. */
    public Optional<String> exceeded(RunState state) {
        return RunBudgets.resolve(policyDefaults, state.graph().budgets()).exceeded(state, clock.instant());
    }
}
