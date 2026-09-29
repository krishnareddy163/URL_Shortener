package com.example.agentic.core.gate;

import com.example.agentic.core.agent.Proposal;
import com.example.agentic.core.graph.Node;
import com.example.agentic.core.state.Artifact;
import com.example.agentic.core.workspace.Diff;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Inputs to a gate evaluation.
 *
 * @param node      node being gated
 * @param agentId   agent that produced the proposal (the node's agent for entry gates)
 * @param proposal  the proposal under evaluation, or {@code null} for entry gates
 * @param staging   staged tree with the proposal applied, or {@code null} for entry gates
 * @param diff      changes the proposal makes relative to its staging base
 * @param upstream  current artifacts of the node's transitive dependencies, keyed by node id
 * @param workspace promoted workspace root
 * @param logDir    directory for tool output logs
 */
public record GateContext(
        Node node,
        String agentId,
        Proposal proposal,
        Path staging,
        Diff diff,
        Map<String, Artifact> upstream,
        Path workspace,
        Path logDir) {

    public GateContext {
        diff = diff == null ? Diff.empty() : diff;
        upstream = Collections.unmodifiableMap(new LinkedHashMap<>(upstream));
    }

    public boolean isEntry() {
        return proposal == null;
    }
}
