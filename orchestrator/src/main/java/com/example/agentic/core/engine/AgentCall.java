package com.example.agentic.core.engine;

/**
 * One agent invocation within a node execution.
 *
 * @param agentId       agent being called
 * @param attempt       attempt number for this node, agent and input hash
 * @param fallbackRound whether this is the fallback agent's round
 * @param feedback      previous failure text or rejection comment, or {@code null}
 */
record AgentCall(String agentId, int attempt, boolean fallbackRound, String feedback) {
}
