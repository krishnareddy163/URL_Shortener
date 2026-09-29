package com.example.agentic.core.state;

/** Lifecycle status of a node, derived only by folding events. */
public enum NodeStatus {
    PENDING,
    RUNNING,
    AWAITING_APPROVAL,
    AWAITING_CLARIFICATION,
    DONE,
    FAILED,
    SKIPPED
}
