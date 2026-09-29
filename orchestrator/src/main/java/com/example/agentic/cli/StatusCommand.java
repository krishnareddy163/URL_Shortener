package com.example.agentic.cli;

import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.metrics.MetricsCalculator;
import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.Hashing;
import com.example.agentic.core.state.ModelUsage;
import com.example.agentic.core.state.RunState;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

/** {@code status <run>}: node table with status, attempts and artifact hashes. */
@Command(name = "status", mixinStandardHelpOptions = true, description = "Show node status, attempts and hashes.")
final class StatusCommand implements Callable<Integer> {

    @Mixin
    private CommonOptions options;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", description = "Run id.")
    private String runId;

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        try (Orchestrator orchestrator = RunSupport.openExisting(options, runId, new PrintWriter(Writer.nullWriter()))) {
            RunState state = orchestrator.state();
            Map<String, Integer> calls = new HashMap<>();
            for (Event event : orchestrator.log().events()) {
                if (event.type() == EventType.AGENT_CALLED) {
                    calls.merge(event.nodeId(), 1, Integer::sum);
                }
            }
            out.printf("Run %s: %s%s%n%n", runId, state.status(),
                    state.stopReason() == null ? "" : " (" + state.stopReason() + ")");
            out.printf("%-20s %-24s %8s  %-14s %-14s%n", "NODE", "STATUS", "ATTEMPTS", "ARTIFACT", "PENDING");
            for (String id : state.graph().topologicalOrder()) {
                RunState.NodeState node = state.node(id);
                out.printf("%-20s %-24s %8d  %-14s %-14s%n", id, node.status(), calls.getOrDefault(id, 0),
                        Hashing.shortHash(node.currentHash()), Hashing.shortHash(node.pendingHash()));
            }
            out.printf("%nAgent calls %d, attempts %d, active time %d s%n", state.agentCalls(), state.totalAttempts(),
                    state.activeTime(orchestrator.log().clock().instant()).toSeconds());
            ModelUsage usage = MetricsCalculator.calculate(orchestrator.log().events()).modelUsage();
            if (usage.calls() > 0) {
                out.printf("Model calls %d, input tokens %d, output tokens %d%n", usage.calls(), usage.inputTokens(),
                        usage.outputTokens());
            }
            return 0;
        }
    }
}
