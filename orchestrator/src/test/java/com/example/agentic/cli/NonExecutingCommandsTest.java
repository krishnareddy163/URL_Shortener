package com.example.agentic.cli;

import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.agent.Proposal;
import com.example.agentic.core.engine.RunOutcome;
import com.example.agentic.core.graph.Autonomy;
import com.example.agentic.core.state.RunMetadata;
import com.example.agentic.support.ScriptedAgent;
import com.example.agentic.support.TestEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.example.agentic.support.TestEngine.node;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/** Commands that never call an agent work on a LIVE run without an API key; only resume needs one. */
class NonExecutingCommandsTest {
    @TempDir
    Path dir;

    @Test
    void readingAndDecidingOnALiveRunNeedsNoApiKey() throws Exception {
        Path runs = dir.resolve("runs");
        ScriptedAgent reviewer = new ScriptedAgent("reviewer", context -> new Proposal(Map.of(), "looks fine",
                List.of("requirement"), Map.of()));
        try (Orchestrator orchestrator = new TestEngine().agent(reviewer).openSqlite(runs.resolve("test-run"))) {
            RunOutcome outcome = orchestrator.start(TestEngine.graph(node("check", "reviewer", Set.of(),
                    Autonomy.APPROVE_AFTER, 0, null)), dir, new RunMetadata("LIVE", "workflow.yaml", dir.toString(), null));
            assertThat(outcome).isEqualTo(RunOutcome.PAUSED);
        }

        Result status = cli(runs, "status", "test-run");
        assertThat(status.exit()).as(status.err()).isZero();
        Result pending = cli(runs, "pending", "test-run");
        assertThat(pending.exit()).isZero();
        assertThat(pending.out()).contains("=== APPROVAL: check ===");
        assertThat(cli(runs, "approve", "test-run", "check", "--by", "alice", "--comment", "ok").exit()).isZero();
        assertThat(cli(runs, "lineage", "test-run", "check").exit()).isZero();
        assertThat(cli(runs, "report", "test-run").exit()).isZero();

        assumeThat(System.getenv("ANTHROPIC_API_KEY")).as("resume needs the key only when it is absent").isNull();
        Result resume = cli(runs, "resume", "test-run");
        assertThat(resume.exit()).isEqualTo(2);
        assertThat(resume.err()).contains("ANTHROPIC_API_KEY is not set");
    }

    private record Result(int exit, String out, String err) {
    }

    private static Result cli(Path runs, String... arguments) {
        CommandLine cli = OrchestratorCli.commandLine();
        StringWriter out = new StringWriter();
        StringWriter err = new StringWriter();
        cli.setOut(new PrintWriter(out));
        cli.setErr(new PrintWriter(err));
        String[] all = new String[arguments.length + 4];
        System.arraycopy(arguments, 0, all, 0, arguments.length);
        all[arguments.length] = "--runs-dir";
        all[arguments.length + 1] = runs.toString();
        all[arguments.length + 2] = "--policies";
        all[arguments.length + 3] = Path.of(System.getProperty("repo.root", "..")).resolve("policies/policies.yaml").toString();
        int exit = cli.execute(all);
        return new Result(exit, out.toString(), err.toString());
    }
}
