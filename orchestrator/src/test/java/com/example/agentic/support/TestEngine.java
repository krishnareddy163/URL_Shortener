package com.example.agentic.support;

import com.example.agentic.core.EngineConfig;
import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.agent.Agent;
import com.example.agentic.core.agent.AgentResolver;
import com.example.agentic.core.gate.ArtifactMetadataGate;
import com.example.agentic.core.gate.Gate;
import com.example.agentic.core.gate.GateContext;
import com.example.agentic.core.gate.GateRegistry;
import com.example.agentic.core.gate.GateResult;
import com.example.agentic.core.graph.Autonomy;
import com.example.agentic.core.graph.Node;
import com.example.agentic.core.graph.WorkflowGraph;
import com.example.agentic.core.policy.PolicyConfig;
import com.example.agentic.core.policy.RiskRule;
import com.example.agentic.core.state.Event;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

/** Builds engines for tests without Maven: in-process gates, scripted agents, a controllable clock. */
public final class TestEngine {
    public static final String FAIL_MARKER = "FAIL";

    private final Map<String, Agent> agents = new LinkedHashMap<>();
    private final GateRegistry gates = new GateRegistry();
    private PolicyConfig policy = policy(Map.of());
    private MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final List<Event> trace = new ArrayList<>();

    public TestEngine() {
        gates.register(new ArtifactMetadataGate());
        gates.register(new ContentGate());
    }

    /** Policy with every scripted agent allowed to write {@code **}. */
    public static PolicyConfig policy(Map<String, Integer> budgets) {
        return new PolicyConfig(budgets, Map.of("worker", List.of("**"), "fallback", List.of("**"),
                "requirements", List.of(), "reviewer", List.of()), List.of(), List.of(), List.of(), List.of(), List.of(),
                new PolicyConfig.Risk(200, List.of("**/db/migration/**")), null, null);
    }

    public TestEngine agent(Agent agent) {
        agents.put(agent.id(), agent);
        return this;
    }

    public TestEngine gate(Gate gate) {
        gates.register(gate);
        return this;
    }

    public TestEngine policy(PolicyConfig newPolicy) {
        this.policy = newPolicy;
        return this;
    }

    public TestEngine clock(MutableClock newClock) {
        this.clock = newClock;
        return this;
    }

    public MutableClock clock() {
        return clock;
    }

    public List<Event> trace() {
        return trace;
    }

    public GateRegistry gates() {
        return gates;
    }

    public EngineConfig config() {
        Consumer<Event> listener = event -> {
            synchronized (trace) {
                trace.add(event);
            }
        };
        AgentResolver resolver = new AgentResolver() {
            @Override
            public Optional<Agent> resolve(String agentId) {
                return Optional.ofNullable(agents.get(agentId));
            }

            @Override
            public Set<String> agentIds() {
                return agents.keySet();
            }
        };
        return new EngineConfig(policy, gates, RiskRule.defaults(policy), resolver, clock, listener);
    }

    public Orchestrator open(Path runDir, InMemoryEventStore store) {
        return Orchestrator.open(runDir, "test-run", config(), store, store);
    }

    public Orchestrator openSqlite(Path runDir) {
        return Orchestrator.open(runDir, "test-run", config());
    }

    public static Node node(String id, String agent, Set<String> dependsOn, Autonomy autonomy, int maxRetries,
                            String fallback, String... exitGates) {
        return new Node(id, agent, dependsOn, List.of(), List.of(exitGates), autonomy, maxRetries, fallback, null);
    }

    public static WorkflowGraph graph(Node... nodes) {
        return new WorkflowGraph("test", "Build something testable", "empty", Map.of(), List.of(nodes));
    }

    /** Fails when any proposed file contains {@link #FAIL_MARKER}; the failure text is the file's first line. */
    public static final class ContentGate implements Gate {
        @Override
        public String id() {
            return "content";
        }

        @Override
        public GateResult evaluate(GateContext context) {
            for (Map.Entry<String, String> file : context.proposal().files().entrySet()) {
                if (file.getValue().contains(FAIL_MARKER)) {
                    return GateResult.fail(id(), file.getKey() + ": " + file.getValue().lines().findFirst().orElse(""));
                }
            }
            return GateResult.pass();
        }
    }
}
