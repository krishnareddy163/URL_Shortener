package com.example.agentic.scenarios;

import com.example.agentic.agents.AgentRegistry;
import com.example.agentic.core.EngineConfig;
import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.engine.RunOutcome;
import com.example.agentic.core.gate.BuildRunner;
import com.example.agentic.core.gate.GateRegistry;
import com.example.agentic.core.graph.GraphValidator;
import com.example.agentic.core.graph.WorkflowGraph;
import com.example.agentic.core.graph.WorkflowLoader;
import com.example.agentic.core.metrics.MetricsCalculator;
import com.example.agentic.core.metrics.RunMetrics;
import com.example.agentic.core.policy.PolicyConfig;
import com.example.agentic.core.policy.RiskRule;
import com.example.agentic.core.report.ReportWriter;
import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.RunMetadata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;

/** Runs a checked-in scenario in MOCK mode with the real policy file and real (Maven-executing) gates. */
final class ScenarioHarness implements AutoCloseable {
    static final String APPROVER = "demo-reviewer";

    private final Orchestrator orchestrator;
    private final WorkflowGraph graph;
    private final Path runDir;

    ScenarioHarness(String scenario, Path runsDir) throws Exception {
        Path scenarioDir = repoRoot().resolve("scenarios").resolve(scenario);
        PolicyConfig policy = PolicyConfig.load(repoRoot().resolve("policies/policies.yaml"));
        AgentRegistry agents = AgentRegistry.mock(scenarioDir.resolve("fixtures"));
        GateRegistry gates = GateRegistry.builtIns(policy, new BuildRunner(policy.build()));
        graph = new WorkflowLoader(new GraphValidator(agents.agentIds(), gates.ids())).load(scenarioDir.resolve("workflow.yaml"));
        runDir = runsDir.resolve(scenario);
        orchestrator = Orchestrator.open(runDir, scenario + "-test",
                new EngineConfig(policy, gates, RiskRule.defaults(policy), agents, Clock.systemUTC(), null));
    }

    static Path repoRoot() {
        return Path.of(System.getProperty("repo.root", "..")).toAbsolutePath().normalize();
    }

    RunOutcome start() throws Exception {
        return keepIncident(orchestrator.start(graph, repoRoot(), RunMetadata.none()));
    }

    RunOutcome resume() throws Exception {
        return keepIncident(orchestrator.resume());
    }

    RunOutcome approveAndResume(String node) throws Exception {
        orchestrator.approvals().approve(node, APPROVER, "approved " + node + " in scenario test", null);
        return resume();
    }

    /** Copies incident.md and gate logs out of the temp dir so an unexpected safe-stop can be diagnosed. */
    private RunOutcome keepIncident(RunOutcome outcome) throws java.io.IOException {
        Path incident = runDir.resolve("incident.md");
        if (outcome == RunOutcome.SAFE_STOPPED && Files.exists(incident)) {
            Path kept = Path.of("target", "scenario-incidents", runDir.getFileName() + "-" + System.nanoTime());
            Files.createDirectories(kept);
            Files.copy(incident, kept.resolve("incident.md"));
            Path logs = runDir.resolve("gate-logs");
            if (Files.isDirectory(logs)) {
                try (var files = Files.list(logs)) {
                    for (Path log : files.toList()) {
                        Files.copy(log, kept.resolve(log.getFileName()));
                    }
                }
            }
        }
        return outcome;
    }

    Orchestrator orchestrator() {
        return orchestrator;
    }

    Path workspace() {
        return orchestrator.workspace().root();
    }

    List<Event> events() {
        return orchestrator.log().events();
    }

    List<Event> events(String node, EventType type) {
        return events().stream().filter(event -> event.type() == type && node.equals(event.nodeId())).toList();
    }

    long count(EventType type) {
        return events().stream().filter(event -> event.type() == type).count();
    }

    long seq(String node, EventType type) {
        return events(node, type).getFirst().seq();
    }

    RunMetrics metrics() {
        return MetricsCalculator.calculate(events());
    }

    String report() throws Exception {
        return Files.readString(ReportWriter.write(runDir, orchestrator.log().runId(), events(), orchestrator.artifacts()));
    }

    @Override
    public void close() {
        orchestrator.close();
    }
}
