package com.example.agentic.cli;

import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.metrics.MetricsCalculator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.concurrent.Callable;

/** {@code report <run>}: writes {@code report.md} and prints the metrics table. */
@Command(name = "report", mixinStandardHelpOptions = true, description = "Generate report.md and print metrics.")
final class ReportCommand implements Callable<Integer> {

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
            out.println(MetricsCalculator.calculate(orchestrator.log().events()).toMarkdown());
            RunSupport.writeReport(orchestrator, out);
            return 0;
        }
    }
}
