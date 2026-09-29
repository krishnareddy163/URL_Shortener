package com.example.agentic.core.gate;

/** Outcome of a gate: pass, or fail with a reason and a normalized failure signature. */
public sealed interface GateResult {

    static GateResult pass() {
        return new Pass(null);
    }

    /** A pass that records evidence in the audit log and the report, for example measured coverage. */
    static GateResult pass(String detail) {
        return new Pass(detail);
    }

    static GateResult fail(String gateId, String reason) {
        return new Fail(gateId, reason, FailureSignature.of(gateId, reason));
    }

    default boolean passed() {
        return this instanceof Pass;
    }

    /**
     * The gate passed.
     *
     * @param detail evidence worth recording (for example measured coverage), or {@code null}
     */
    record Pass(String detail) implements GateResult {
    }

    /**
     * The gate failed.
     *
     * @param gateId    failing gate
     * @param reason    human-readable failure (fed back to the agent on retry)
     * @param signature {@link FailureSignature} used by the circuit breaker
     */
    record Fail(String gateId, String reason, String signature) implements GateResult {
    }
}
