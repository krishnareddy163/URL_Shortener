package com.example.agentic.cli;

import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.report.LineageWalker;
import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.RunState;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.concurrent.Callable;

/** {@code lineage <run> <artifactOrNode>}: prints the decision chain back to the requirement. */
@Command(name = "lineage", mixinStandardHelpOptions = true, description = "Show the decision chain of an artifact or node.")
final class LineageCommand implements Callable<Integer> {

    @Mixin
    private CommonOptions options;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", description = "Run id.")
    private String runId;

    @Parameters(index = "1", description = "Node id, artifact hash, or hash prefix (12+ characters).")
    private String target;

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        try (Orchestrator orchestrator = RunSupport.openExisting(options, runId, new PrintWriter(Writer.nullWriter()))) {
            RunState state = orchestrator.state();
            String hash = resolve(state, orchestrator);
            new LineageWalker(orchestrator.artifacts()).chain(hash, state.graph().requirement()).forEach(out::println);
            return 0;
        }
    }

    private String resolve(RunState state, Orchestrator orchestrator) {
        if (state.graph().node(target).isPresent()) {
            String current = state.node(target).currentHash();
            if (current == null) {
                throw new IllegalArgumentException("node '" + target + "' has no artifact yet");
            }
            return current;
        }
        if (target.length() < 12) {
            throw new IllegalArgumentException("'" + target + "' is not a node id and too short to be a hash prefix");
        }
        for (Event event : orchestrator.log().events()) {
            if (event.outputHash() != null && event.outputHash().startsWith(target)) {
                return event.outputHash();
            }
        }
        throw new IllegalArgumentException("no artifact matches '" + target + "'");
    }
}
