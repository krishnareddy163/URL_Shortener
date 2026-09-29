package com.example.agentic.cli;

import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.approval.ApprovalService;
import com.example.agentic.core.state.RunState;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.List;
import java.util.concurrent.Callable;

/** {@code pending <run>}: everything waiting for a human, with summary, risk reasons, files, diff and hash. */
@Command(name = "pending", mixinStandardHelpOptions = true, description = "Show approvals and questions waiting for a human.")
final class PendingCommand implements Callable<Integer> {

    @Mixin
    private CommonOptions options;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", description = "Run id.")
    private String runId;

    @Option(names = "--max-diff-lines", defaultValue = "-1",
            description = "Truncate each diff to this many lines (default: show all).")
    private int maxDiffLines;

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        try (Orchestrator orchestrator = RunSupport.openExisting(options, runId, new PrintWriter(Writer.nullWriter()))) {
            List<ApprovalService.PendingApproval> approvals = orchestrator.pendingApprovals();
            List<RunState.Question> questions = orchestrator.openQuestions();
            if (approvals.isEmpty() && questions.isEmpty()) {
                out.println("Nothing is waiting for a human in run " + runId + ".");
                return 0;
            }
            for (ApprovalService.PendingApproval approval : approvals) {
                out.printf("=== APPROVAL: %s ===%n", approval.nodeId());
                out.printf("Artifact hash: %s%n", approval.hash());
                out.printf("Summary:       %s%n", approval.summary());
                out.println("Why approval is required:");
                approval.reasons().forEach(reason -> out.println("  - " + reason));
                out.println("Files to promote:");
                approval.files().forEach(file -> out.println("  - " + file));
                out.println("Diff stat:");
                out.print(approval.diffStat() == null ? "" : approval.diffStat().indent(2));
                out.println("Diff:");
                out.println(approval.diff() == null || approval.diff().isEmpty() ? "  (no file changes)" : truncate(approval.diff()));
                out.printf("Approve: orchestrator approve %s %s --by <name> --comment \"...\" [--hash %s]%n",
                        runId, approval.nodeId(), approval.hash().substring(0, 12));
                out.printf("Reject:  orchestrator reject %s %s --by <name> --comment \"what to change\"%n%n",
                        runId, approval.nodeId());
            }
            for (RunState.Question question : questions) {
                out.printf("=== QUESTION: %s (node %s, %s) ===%n", question.id(), question.nodeId(),
                        question.blocking() ? "blocking" : "non-blocking");
                out.println(question.text());
                if (!question.options().isEmpty()) {
                    out.println("Options: " + String.join(", ", question.options()));
                }
                out.printf("Answer: orchestrator answer %s %s \"<answer>\" --by <name>%n%n", runId, question.id());
            }
            return 0;
        }
    }

    private String truncate(String diff) {
        List<String> lines = diff.lines().toList();
        if (maxDiffLines < 0 || lines.size() <= maxDiffLines) {
            return diff;
        }
        return String.join("\n", lines.subList(0, maxDiffLines))
                + "\n... (" + (lines.size() - maxDiffLines) + " more lines; rerun without --max-diff-lines to see all)";
    }
}
