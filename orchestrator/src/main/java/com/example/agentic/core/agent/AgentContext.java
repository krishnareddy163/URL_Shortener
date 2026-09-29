package com.example.agentic.core.agent;

import com.example.agentic.core.state.Artifact;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Everything an agent may see for one attempt. Upstream artifacts are limited to the node's transitive
 * dependencies, so an agent cannot read outputs it does not depend on.
 *
 * @param runId         run identifier
 * @param nodeId        node being executed
 * @param agentId       agent being called
 * @param attempt       1-based attempt number for this node, agent and input hash
 * @param fallbackRound {@code true} when the node's fallback agent is being used
 * @param variantKey    fixture variant selected by a clarification answer, or {@code default}
 * @param requirement   the workflow requirement text
 * @param upstream      current artifacts of transitive dependencies, keyed by node id
 * @param answers       clarification answers keyed by question id
 * @param feedback      gate failure text or rejection comment from the previous attempt, or {@code null}
 * @param workspace     read-only view of the promoted workspace
 * @param task          what this node must achieve (from the workflow), or {@code null}
 * @param usage         where model clients report the tokens and time this attempt uses
 */
public record AgentContext(
        String runId,
        String nodeId,
        String agentId,
        int attempt,
        boolean fallbackRound,
        String variantKey,
        String requirement,
        Map<String, Artifact> upstream,
        Map<String, String> answers,
        String feedback,
        WorkspaceView workspace,
        String task,
        UsageMeter usage) {

    public AgentContext {
        usage = usage == null ? new UsageMeter() : usage;
        upstream = Collections.unmodifiableMap(new LinkedHashMap<>(upstream));
        answers = Collections.unmodifiableMap(new LinkedHashMap<>(answers));
    }

    /** A context with no task description and its own fresh {@link UsageMeter}. */
    public AgentContext(String runId, String nodeId, String agentId, int attempt, boolean fallbackRound, String variantKey,
                        String requirement, Map<String, Artifact> upstream, Map<String, String> answers, String feedback,
                        WorkspaceView workspace) {
        this(runId, nodeId, agentId, attempt, fallbackRound, variantKey, requirement, upstream, answers, feedback, workspace,
                null, new UsageMeter());
    }
}
