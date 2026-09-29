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

/** {@code answer <run> <questionId> "<text>" --by X}: answers (or changes the answer to) a clarification. */
@Command(name = "answer", mixinStandardHelpOptions = true, description = "Answer a clarification question.")
final class AnswerCommand implements Callable<Integer> {

    @Mixin
    private CommonOptions options;

    @Spec
    private CommandSpec spec;

    @Parameters(index = "0", description = "Run id.")
    private String runId;

    @Parameters(index = "1", description = "Question id.")
    private String questionId;

    @Parameters(index = "2", description = "Answer text.")
    private String answer;

    @Option(names = "--by", required = true, description = "Who is answering (recorded in the audit log).")
    private String by;

    @Override
    public Integer call() throws Exception {
        PrintWriter out = spec.commandLine().getOut();
        try (Orchestrator orchestrator = RunSupport.openExisting(options, runId, out)) {
            String result = orchestrator.clarifications().answer(questionId, answer, by);
            out.printf("%s%nContinue with: orchestrator resume %s%n", result, runId);
            RunSupport.writeReport(orchestrator, out);
            return 0;
        }
    }
}
