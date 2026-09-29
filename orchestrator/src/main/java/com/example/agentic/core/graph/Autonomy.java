package com.example.agentic.core.graph;

/**
 * How much human oversight a node requires before its output is promoted.
 *
 * <ul>
 *   <li>{@link #AUTO}: promote when all exit gates pass.</li>
 *   <li>{@link #APPROVE_AFTER}: always wait for a hash-bound human approval.</li>
 *   <li>{@link #ESCALATE_ON_RISK}: wait for approval only if a risk rule flags the diff.</li>
 * </ul>
 */
public enum Autonomy {
    AUTO,
    APPROVE_AFTER,
    ESCALATE_ON_RISK
}
