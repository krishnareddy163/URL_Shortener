package com.example.agentic.cli;

import com.example.agentic.agents.AgentRegistry;
import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.engine.RunOutcome;
import com.example.agentic.core.gate.GateRegistry;
import com.example.agentic.core.graph.GraphValidator;
import com.example.agentic.core.graph.WorkflowGraph;
import com.example.agentic.core.graph.WorkflowLoader;
import com.example.agentic.core.policy.PolicyConfig;
import com.example.agentic.core.state.Hashing;
import com.example.agentic.core.state.RunMetadata;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.Callable;

/** {@code run <workflow.yaml>}: validates the workflow and starts a new run with a live trace. */
@Command(name = "run", mixinStandardHelpOptions = true, description = "Start a run of a workflow.")
final class RunCommand implements Callable<Integer> {

    @Mixin
    private CommonOptions options;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", description = "Workflow YAML (fixtures are read from its sibling 'fixtures/' directory).")
    private Path workflow;

    @Option(names = "--run-id", description = "Run id (default: <workflow>-<utc timestamp>).")
    private String runId;

    @Option(names = "--mode", defaultValue = "MOCK", description = "Agent mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private RunSupport.Mode mode;

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        PolicyConfig policy = PolicyConfig.load(options.policies);
        Path fixtureRoot = Objects.requireNonNull(workflow.toAbsolutePath().normalize().getParent()).resolve("fixtures");
        AgentRegistry agents = RunSupport.agents(mode, fixtureRoot, policy);
        GateRegistry gates = RunSupport.gates(policy);
        WorkflowGraph graph = new WorkflowLoader(new GraphValidator(agents.agentIds(), gates.ids())).load(workflow);
        if (mode == RunSupport.Mode.LIVE) {
            graph = RunSupport.withLiveFallbacks(graph);
        }
        String id = runId != null ? runId
                : graph.name() + "-" + LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path runDir = RunSupport.runDir(options, id);
        if (Files.exists(runDir.resolve("events.db"))) {
            throw new IllegalArgumentException("run '" + id + "' already exists; use 'resume " + id + "'");
        }
        RunMetadata metadata = new RunMetadata(mode.name(), workflow.toString(), fixtureRoot.toString(),
                Hashing.sha256(Files.readAllBytes(options.policies)));
        out.printf("Run %s: workflow '%s' (%d nodes, mode %s)%n", id, graph.name(), graph.nodes().size(), mode);
        out.println("Isolation: " + RunSupport.buildIsolation(policy));
        try (Orchestrator orchestrator = Orchestrator.open(runDir, id, RunSupport.config(options, policy, agents, out))) {
            RunOutcome outcome = orchestrator.start(graph, options.repoRoot, metadata);
            OutcomePrinter.print(out, id, outcome, orchestrator);
            RunSupport.writeReport(orchestrator, out);
            return outcome.exitCode();
        }
    }
}
