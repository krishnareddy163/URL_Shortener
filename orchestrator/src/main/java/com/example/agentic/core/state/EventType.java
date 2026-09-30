package com.example.agentic.core.state;

/** The closed set of facts that can be appended to a run's event log. */
public enum EventType {
    RUN_STARTED,
    NODE_STARTED,
    AGENT_CALLED,
    GATE_PASSED,
    GATE_FAILED,
    ATTEMPT_DISCARDED,
    FALLBACK,
    NODE_FAILED,
    APPROVAL_REQUESTED,
    APPROVED,
    REJECTED,
    CLARIFICATION_REQUESTED,
    ANSWERED,
    INVALIDATED,
    UPSTREAM_REJECTED,
    REPLAN,
    NODE_DONE,
    RUN_PAUSED,
    RESUMED,
    SAFE_STOP,
    RUN_COMPLETED
}
