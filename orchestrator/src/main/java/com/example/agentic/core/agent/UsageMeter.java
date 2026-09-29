package com.example.agentic.core.agent;

import com.example.agentic.core.state.ModelUsage;

import java.time.Duration;

/**
 * Collects the model usage of one attempt. The engine creates a fresh meter per attempt and hands it to the agent
 * in its {@link AgentContext}; model clients add to it, and the engine records the total in {@code AGENT_CALLED}
 * whether or not the attempt succeeded. Thread-safe.
 */
public final class UsageMeter {
    private ModelUsage total = ModelUsage.NONE;

    /** Adds one model request. */
    public synchronized void record(long inputTokens, long outputTokens, Duration elapsed) {
        total = total.plus(new ModelUsage(1, inputTokens, outputTokens, elapsed.toMillis()));
    }

    /** Usage so far. */
    public synchronized ModelUsage total() {
        return total;
    }
}
