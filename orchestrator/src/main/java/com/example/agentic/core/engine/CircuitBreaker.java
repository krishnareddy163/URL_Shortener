package com.example.agentic.core.engine;

/**
 * Stops retrying an agent that fails the same way twice in a row: identical consecutive failure signatures
 * mean feedback is not helping. One breaker exists per node execution and agent.
 */
public final class CircuitBreaker {
    static final int TRIP_THRESHOLD = 2;

    private String lastSignature;
    private int consecutive;

    /** Records a failure and returns {@code true} if the breaker is now open. */
    public boolean recordFailure(String signature) {
        if (signature.equals(lastSignature)) {
            consecutive++;
        } else {
            lastSignature = signature;
            consecutive = 1;
        }
        return consecutive >= TRIP_THRESHOLD;
    }
}
