package com.example.agentic.agents;

import com.example.agentic.agents.live.AnthropicClient;
import com.example.agentic.agents.live.LiveAnalystReasoner;
import com.example.agentic.agents.live.LiveBuilderAgent;
import com.example.agentic.agents.live.LiveRequirementsAgent;
import com.example.agentic.agents.live.LiveReviewerAgent;
import com.example.agentic.agents.scan.ImpactReasoner;
import com.example.agentic.core.agent.Agent;
import com.example.agentic.core.agent.AgentResolver;
import com.example.agentic.core.policy.PolicyConfig;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builds the agent set for a run. MOCK mode: every agent is fixture-driven (the analyst adds a real
 * JavaParser scan). LIVE mode: every role calls the model, and a fixture agent {@code mock-<id>} is registered as
 * each one's fallback. The analyst's JavaParser scan is real in both modes; LIVE only replaces its reasoning.
 */
public final class AgentRegistry implements AgentResolver {
    private static final String REQUIREMENTS = "requirements";
    private static final String REVIEWER = "reviewer";
    private static final String ANALYST = "analyst";
    public static final List<String> AGENT_IDS = List.of(
            REQUIREMENTS, "architect", ANALYST, "developer", "tester", "docs", REVIEWER);
    private static final List<String> LIVE_IDS = List.of(REQUIREMENTS, ANALYST, "architect", "developer", "tester", "docs",
            REVIEWER);

    private final Map<String, Agent> agents;

    private AgentRegistry(Map<String, Agent> agents) {
        this.agents = Map.copyOf(agents);
    }

    public static AgentRegistry mock(Path fixtureRoot) {
        return new AgentRegistry(mockAgents(fixtureRoot));
    }

    /**
     * LIVE agents plus their fixture fallbacks.
     *
     * @throws IllegalStateException if the policy gives a file-producing agent or any fallback no write scope,
     *                               which would make every one of its attempts fail
     */
    public static AgentRegistry live(Path fixtureRoot, AnthropicClient client, PolicyConfig policy) {
        Map<String, Agent> agents = mockAgents(fixtureRoot);
        for (String id : LIVE_IDS) {
            requireScope(policy, fallbackFor(id));
            agents.put(fallbackFor(id), new MockAgent(fallbackFor(id), fixtureRoot));
        }
        agents.put(fallbackFor(ANALYST), new CodebaseAnalystAgent(fallbackFor(ANALYST),
                ImpactReasoner.replaying(new MockAgent(fallbackFor(ANALYST), fixtureRoot))));
        agents.put(ANALYST, new CodebaseAnalystAgent(ANALYST, new LiveAnalystReasoner(client)));
        agents.put(REQUIREMENTS, new LiveRequirementsAgent(client));
        agents.put(REVIEWER, new LiveReviewerAgent(client));
        for (Map.Entry<String, String> role : LiveBuilderAgent.briefs().entrySet()) {
            agents.put(role.getKey(), new LiveBuilderAgent(role.getKey(), role.getValue(),
                    requireScope(policy, role.getKey()), client));
        }
        return new AgentRegistry(agents);
    }

    private static List<String> requireScope(PolicyConfig policy, String agentId) {
        return policy.scopeFor(agentId).orElseThrow(() -> new IllegalStateException(
                "policies.yaml has no pathScopes entry for LIVE agent '" + agentId + "'"));
    }

    /**
     * The agent ids a MOCK or LIVE registry supplies, without building any agent, so commands that never call an agent
     * can validate a run's graph with no API key.
     */
    public static Set<String> ids(boolean live) {
        Set<String> ids = new LinkedHashSet<>(AGENT_IDS);
        if (live) {
            LIVE_IDS.forEach(id -> ids.add(fallbackFor(id)));
        }
        return Collections.unmodifiableSet(ids);
    }

    /** In LIVE mode, the fixture agent a live agent falls back to after malformed or failing replies. */
    public static Map<String, String> liveFallbacks() {
        Map<String, String> fallbacks = new LinkedHashMap<>();
        LIVE_IDS.forEach(id -> fallbacks.put(id, fallbackFor(id)));
        return fallbacks;
    }

    @Override
    public Optional<Agent> resolve(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    @Override
    public Set<String> agentIds() {
        return agents.keySet();
    }

    private static Map<String, Agent> mockAgents(Path fixtureRoot) {
        Map<String, Agent> agents = new LinkedHashMap<>();
        for (String id : AGENT_IDS) {
            agents.put(id, new MockAgent(id, fixtureRoot));
        }
        agents.put(ANALYST, new CodebaseAnalystAgent(ANALYST, ImpactReasoner.replaying(new MockAgent(ANALYST, fixtureRoot))));
        return agents;
    }

    private static String fallbackFor(String id) {
        return "mock-" + id;
    }
}
