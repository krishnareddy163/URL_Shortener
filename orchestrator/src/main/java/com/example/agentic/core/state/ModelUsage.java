package com.example.agentic.core.state;

/**
 * Model usage of one agent attempt, as reported by the model API. Recorded in {@code AGENT_CALLED} for LIVE
 * agents (absent for fixture agents), including attempts whose reply was rejected, because failed calls cost too.
 *
 * @param calls        model requests made
 * @param inputTokens  prompt tokens billed
 * @param outputTokens reply tokens billed
 * @param millis       wall-clock time spent waiting on the model
 */
public record ModelUsage(int calls, long inputTokens, long outputTokens, long millis) {

    public static final ModelUsage NONE = new ModelUsage(0, 0, 0, 0);

    /** Component-wise sum. */
    public ModelUsage plus(ModelUsage other) {
        return new ModelUsage(calls + other.calls, inputTokens + other.inputTokens, outputTokens + other.outputTokens,
                millis + other.millis);
    }
}
