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

/** {@code approve <run> <node> --by X --comment "..."}: hash-bound approval and promotion. */
@Command(name = "approve", mixinStandardHelpOptions = true, description = "Approve a node's pending artifact (hash-bound).")
final class ApproveCommand implements Callable<Integer> {

    @Mixin
    private CommonOptions options;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", description = "Run id.")
    private String runId;

    @Parameters(index = "1", description = "Node id.")
    private String nodeId;

    @Option(names = "--by", required = true, description = "Approver identity (recorded in the audit log).")
    private String by;

    @Option(names = "--comment", defaultValue = "", description = "Approval comment.")
    private String comment;

    @Option(names = "--hash", description = "Artifact hash (or 12+ char prefix) you reviewed; rejected if it no longer matches.")
    private String hash;

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        try (Orchestrator orchestrator = RunSupport.openExisting(options, runId, out)) {
            orchestrator.approvals().approve(nodeId, by, comment, hash);
            out.printf("Approved %s by %s. Continue with: orchestrator resume %s%n", nodeId, by, runId);
            RunSupport.writeReport(orchestrator, out);
            return 0;
        }
    }
}
