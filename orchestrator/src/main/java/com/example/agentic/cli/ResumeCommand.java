package com.example.agentic.cli;

import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.engine.RunOutcome;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

/** {@code resume <run>}: continues after approvals, answers, a fixed safe-stop cause, or a crash. */
@Command(name = "resume", mixinStandardHelpOptions = true, description = "Continue a paused, stopped or interrupted run.")
final class ResumeCommand implements Callable<Integer> {

    @Mixin
    private CommonOptions options;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", description = "Run id.")
    private String runId;

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        try (Orchestrator orchestrator = RunSupport.openForExecution(options, runId, out)) {
            RunOutcome outcome = orchestrator.resume();
            OutcomePrinter.print(out, runId, outcome, orchestrator);
            RunSupport.writeReport(orchestrator, out);
            return outcome.exitCode();
        }
    }
}
