package com.example.agentic.agents.scan;

import com.example.agentic.core.agent.Agent;
import com.example.agentic.core.agent.AgentContext;
import com.example.agentic.core.agent.AgentException;
import com.example.agentic.core.agent.Proposal;

import java.util.Map;

/**
 * The reasoning half of codebase analysis: given the requirement and a real scan of the workspace, decide which
 * files are impacted and why (and optionally propose a graph patch). The scan itself is never delegated.
 */
public interface ImpactReasoner {

    /**
     * @param context the analyst's agent context
     * @param scan    the real JavaParser scan of the workspace
     * @return a proposal whose {@code data} holds {@code impact[]} and optionally {@code graphPatch}
     */
    Proposal reason(AgentContext context, CodebaseScan scan) throws AgentException;

    /** Audit metadata for {@code AGENT_CALLED}; never secrets. */
    Map<String, String> metadata();

    /** Reasoning replayed by a fixture-driven agent (MOCK mode, and the LIVE fallback); it does not need the scan. */
    static ImpactReasoner replaying(Agent fixtures) {
        return new ImpactReasoner() {
            @Override
            public Proposal reason(AgentContext context, CodebaseScan scan) throws AgentException {
                return fixtures.propose(context);
            }

            @Override
            public Map<String, String> metadata() {
                return fixtures.metadata();
            }
        };
    }
}
