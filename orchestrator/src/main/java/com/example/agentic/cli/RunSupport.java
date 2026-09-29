package com.example.agentic.cli;

import com.example.agentic.agents.AgentRegistry;
import com.example.agentic.agents.live.AnthropicClient;
import com.example.agentic.agents.live.JdkHttpTransport;
import com.example.agentic.core.EngineConfig;
import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.agent.Agent;
import com.example.agentic.core.agent.AgentResolver;
import com.example.agentic.core.gate.BuildRunner;
import com.example.agentic.core.gate.GateRegistry;
import com.example.agentic.core.graph.Node;
import com.example.agentic.core.graph.WorkflowGraph;
import com.example.agentic.core.policy.PolicyConfig;
import com.example.agentic.core.policy.RiskRule;
import com.example.agentic.core.report.ReportWriter;
import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunMetadata;
import com.example.agentic.core.state.SqliteEventStore;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/** Wiring shared by the commands: resolving runs, building agents, gates and policy for a mode. */
final class RunSupport {
    private static final Pattern RUN_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,80}");

    /** Agent execution mode. */
    enum Mode { MOCK, LIVE }

    private RunSupport() {
    }

    static Path runDir(CommonOptions options, String runId) {
        if (!RUN_ID.matcher(runId).matches()) {
            throw new IllegalArgumentException("run id must match " + RUN_ID.pattern());
        }
        Path root = options.runsDir.toAbsolutePath().normalize();
        return root.resolve(runId);
    }

    static AgentRegistry agents(Mode mode, Path fixtureRoot, PolicyConfig policy) {
        if (mode == Mode.LIVE) {
            return AgentRegistry.live(fixtureRoot, AnthropicClient.fromEnvironment(System.getenv(), new JdkHttpTransport()),
                    policy);
        }
        return AgentRegistry.mock(fixtureRoot);
    }

    static GateRegistry gates(PolicyConfig policy) {
        return GateRegistry.builtIns(policy, new BuildRunner(policy.build()));
    }

    /** Where this machine will run gate builds, for the run banner. */
    static String buildIsolation(PolicyConfig policy) {
        return new BuildRunner(policy.build()).describe();
    }

    static EngineConfig config(CommonOptions options, PolicyConfig policy, AgentResolver agents, PrintWriter out) {
        return new EngineConfig(policy, gates(policy), RiskRule.defaults(policy), agents, Clock.systemUTC(),
                new ConsoleTrace(out, options.noColor));
    }

    /** In LIVE mode, gives each live-agent node its fixture fallback unless the workflow already set one. */
    static WorkflowGraph withLiveFallbacks(WorkflowGraph graph) {
        Map<String, String> fallbacks = AgentRegistry.liveFallbacks();
        List<Node> nodes = graph.nodes().stream()
                .map(node -> node.fallbackAgent() == null && fallbacks.containsKey(node.agent())
                        ? node.withFallbackAgent(fallbacks.get(node.agent())) : node)
                .toList();
        return graph.withNodes(nodes);
    }

    /**
     * Opens an existing run for a command that records decisions or reads state but never calls an agent. Agents are
     * not built, so a LIVE run needs no API key here; resolving one fails instead of silently using another.
     */
    static Orchestrator openExisting(CommonOptions options, String runId, PrintWriter out) throws IOException {
        return open(options, runId, out, false);
    }

    /** Opens an existing run to execute it, rebuilding agents from the mode and fixtures recorded in RUN_STARTED. */
    static Orchestrator openForExecution(CommonOptions options, String runId, PrintWriter out) throws IOException {
        return open(options, runId, out, true);
    }

    private static Orchestrator open(CommonOptions options, String runId, PrintWriter out, boolean runsAgents)
            throws IOException {
        Path runDir = runDir(options, runId);
        if (!Files.isRegularFile(runDir.resolve("events.db"))) {
            throw new IllegalArgumentException("no run '" + runId + "' under " + options.runsDir);
        }
        Event started;
        try (SqliteEventStore store = new SqliteEventStore(runDir.resolve("events.db"))) {
            started = store.read(runId).stream().filter(event -> event.type() == EventType.RUN_STARTED).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("run '" + runId + "' has no RUN_STARTED event"));
        }
        RunMetadata metadata = started.payload(Payload.RunStarted.class).metadata();
        if (metadata == null || metadata.fixtures() == null || metadata.mode() == null) {
            throw new IllegalArgumentException("run '" + runId + "' was not started by this CLI (RUN_STARTED records no "
                    + "mode or fixtures), so its agents cannot be rebuilt");
        }
        Mode mode = Mode.valueOf(metadata.mode());
        PolicyConfig policy = PolicyConfig.load(options.policies);
        AgentResolver agents = runsAgents ? agents(mode, Path.of(metadata.fixtures()), policy)
                : new IdsOnly(AgentRegistry.ids(mode == Mode.LIVE));
        return Orchestrator.open(runDir, runId, config(options, policy, agents, out));
    }

    /**
     * Supplies a run's agent ids for graph validation but refuses to resolve an agent.
     *
     * @param agentIds the ids the run's real registry would supply
     */
    private record IdsOnly(Set<String> agentIds) implements AgentResolver {
        IdsOnly {
            agentIds = Set.copyOf(agentIds);
        }

        @Override
        public Optional<Agent> resolve(String agentId) {
            throw new IllegalStateException("this command does not run agents; use resume");
        }
    }

    static void writeReport(Orchestrator orchestrator, PrintWriter out) throws IOException {
        Path report = ReportWriter.write(orchestrator.runDir(), orchestrator.log().runId(), orchestrator.log().events(),
                orchestrator.artifacts());
        out.println("Report: " + report);
    }
}
