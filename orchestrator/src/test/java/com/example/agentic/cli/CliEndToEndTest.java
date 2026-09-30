package com.example.agentic.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Drives the ambiguous workflow through every human-facing command, in process, with the real gates. */
class CliEndToEndTest {
    private static final Path ROOT = Path.of(System.getProperty("repo.root", "..")).toAbsolutePath().normalize();
    private static final String RUN = "cli-run";

    @TempDir
    Path runs;

    @Test
    void aRunIsStartedAnsweredRejectedApprovedAndInspectedThroughTheCli() {
        Result started = cli("run", ROOT.resolve("scenarios/ambiguous/workflow.yaml").toString(), "--run-id", RUN);
        assertThat(started.exit()).as(started.err()).isEqualTo(10);

        Result clarification = cli("pending", RUN);
        assertThat(clarification.exit()).isZero();
        assertThat(clarification.out()).contains("q-secure");

        Result answered = cli("answer", RUN, "q-secure", "https-only", "--by", "alice");
        assertThat(answered.exit()).as(answered.err()).isZero();
        assertThat(answered.out()).contains("orchestrator resume " + RUN);

        Result paused = cli("resume", RUN);
        assertThat(paused.exit()).as(paused.err()).isEqualTo(10);
        assertThat(cli("pending", RUN).out()).contains("=== APPROVAL: release ===");

        Result rejected = cli("reject", RUN, "release", "--by", "alice", "--comment", "name the version in the title");
        assertThat(rejected.exit()).as(rejected.err()).isZero();
        assertThat(rejected.out()).contains("Rejected release by alice");

        Result status = cli("status", RUN);
        assertThat(status.exit()).isZero();
        assertThat(status.out()).contains("release");

        Result lineage = cli("lineage", RUN, "implement");
        assertThat(lineage.exit()).as(lineage.err()).isZero();
        assertThat(lineage.out()).contains("implement");

        assertThat(cli("report", RUN).exit()).isZero();
    }

    @Test
    void unknownRunsAndNodesAreReportedNotThrown() {
        Result missing = cli("status", "no-such-run");
        assertThat(missing.exit()).isNotZero();
        assertThat(missing.err()).contains("no run 'no-such-run'");

        assertThat(cli("approve", "no-such-run", "release", "--by", "alice", "--comment", "x").exit()).isNotZero();
        assertThat(cli("answer", "no-such-run", "q", "a", "--by", "alice").exit()).isNotZero();
        assertThat(cli("reject", "no-such-run", "n", "--by", "alice", "--comment", "x").exit()).isNotZero();
        assertThat(cli("resume", "no-such-run").exit()).isNotZero();
        assertThat(cli("lineage", "no-such-run", "n").exit()).isNotZero();
        assertThat(cli("pending", "no-such-run").exit()).isNotZero();
        assertThat(cli("report", "no-such-run").exit()).isNotZero();
    }

    @Test
    void anInvalidRunIdIsRefused() {
        Result result = cli("status", "../escape");
        assertThat(result.exit()).isNotZero();
        assertThat(result.err()).contains("run id must match");
    }

    private record Result(int exit, String out, String err) {
    }

    private Result cli(String... arguments) {
        CommandLine cli = OrchestratorCli.commandLine();
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cli.setOut(new PrintWriter(out));
        cli.setErr(new PrintWriter(err));
        String[] all = new String[arguments.length + 7];
        System.arraycopy(arguments, 0, all, 0, arguments.length);
        all[arguments.length] = "--runs-dir";
        all[arguments.length + 1] = runs.toString();
        all[arguments.length + 2] = "--policies";
        all[arguments.length + 3] = ROOT.resolve("policies/policies.yaml").toString();
        all[arguments.length + 4] = "--repo-root";
        all[arguments.length + 5] = ROOT.toString();
        all[arguments.length + 6] = "--no-color";
        int exit = cli.execute(all);
        return new Result(exit, out.toString(), err.toString());
    }
}
