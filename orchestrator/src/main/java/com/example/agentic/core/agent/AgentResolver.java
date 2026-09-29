package com.example.agentic.core.agent;

import java.util.Optional;
import java.util.Set;

/** Looks up agents by id. Implemented outside {@code core} so the engine never depends on agent code. */
public interface AgentResolver {

    Optional<Agent> resolve(String agentId);

    /** Every agent id this resolver can supply; workflows referencing other ids are rejected at load. */
    Set<String> agentIds();
}
