package com.example.agentic.cli;

import com.example.agentic.core.Orchestrator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

/** {@code reject <run> <node> --by X --comment "..."}: discards the pending artifact; the comment becomes feedback. */
@Command(name = "reject", mixinStandardHelpOptions = true, description = "Reject a node's pending artifact and re-run it with feedback.")
final class RejectCommand implements Callable<Integer> {

    @Mixin
    private CommonOptions options;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", description = "Run id.")
    private String runId;

    @Parameters(index = "1", description = "Node id.")
    private String nodeId;

    @Option(names = "--by", required = true, description = "Reviewer identity (recorded in the audit log).")
    private String by;

    @Option(names = "--comment", required = true, description = "What must change (passed to the agent as feedback).")
    private String comment;

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        try (Orchestrator orchestrator = RunSupport.openExisting(options, runId, out)) {
            orchestrator.approvals().reject(nodeId, by, comment);
            out.printf("Rejected %s by %s. The node re-runs with your comment on: orchestrator resume %s%n", nodeId, by, runId);
            RunSupport.writeReport(orchestrator, out);
            return 0;
        }
    }
}
