package com.example.agentic.core.agent;

import java.util.Map;

/** A proposal-producing worker. Implementations must not touch the filesystem or other shared state. */
public interface Agent {

    String id();

    Proposal propose(AgentContext context) throws AgentException;

    /** Audit metadata recorded in {@code AGENT_CALLED} (for example the model used); never secrets. */
    default Map<String, String> metadata() {
        return Map.of();
    }
}
