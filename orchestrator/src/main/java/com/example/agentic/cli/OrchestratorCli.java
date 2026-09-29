package com.example.agentic.cli;

import com.example.agentic.core.approval.StaleApprovalException;
import com.example.agentic.core.graph.WorkflowValidationException;
import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Command-line entry point ({@code java -jar orchestrator.jar}). Exit codes: 0 completed, 10 paused awaiting
 * a human, 20 safe-stopped, 2 usage or validation error.
 */
@Command(name = "orchestrator", mixinStandardHelpOptions = true, version = "orchestrator 1.0.0",
        description = "Governed agentic SDLC orchestrator. Agents propose; the engine disposes; humans approve.",
        subcommands = {RunCommand.class, StatusCommand.class, PendingCommand.class, ApproveCommand.class,
                RejectCommand.class, AnswerCommand.class, ResumeCommand.class, ReportCommand.class, LineageCommand.class})
public final class OrchestratorCli implements Runnable {
    public static final int EXIT_USAGE = 2;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    public static void main(String[] args) {
        System.exit(commandLine().execute(args));
    }

    /** A configured command line (exposed for tests). */
    public static CommandLine commandLine() {
        return new CommandLine(new OrchestratorCli())
                .setExecutionExceptionHandler((exception, commandLine, parseResult) -> {
                    commandLine.getErr().println("error: " + message(exception));
                    commandLine.getErr().flush();
                    return EXIT_USAGE;
                });
    }

    @Override
    public void run() {
        spec.commandLine().usage(spec.commandLine().getOut());
    }

    private static String message(Exception exception) {
        if (exception instanceof WorkflowValidationException invalid) {
            return "invalid workflow:\n  - " + String.join("\n  - ", invalid.errors());
        }
        if (exception instanceof StaleApprovalException stale) {
            return "stale approval: " + stale.getMessage();
        }
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }
}
