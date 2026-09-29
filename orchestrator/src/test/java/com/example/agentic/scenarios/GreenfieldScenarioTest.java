package com.example.agentic.scenarios;

import com.example.agentic.core.engine.RunOutcome;
import com.example.agentic.core.metrics.RunMetrics;
import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.NodeStatus;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.workspace.FileTrees;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Section 9.1: greenfield build with a real failing unit test, retry with feedback, and three approvals. */
@Execution(ExecutionMode.CONCURRENT)
class GreenfieldScenarioTest {

    @TempDir
    Path runs;

    @Test
    void buildsTheShortenerFromScratchUnderGovernance() throws Exception {
        try (ScenarioHarness run = new ScenarioHarness("greenfield", runs)) {
            assertThat(run.start()).isEqualTo(RunOutcome.PAUSED);
            assertThat(run.orchestrator().state().status("design")).isEqualTo(NodeStatus.AWAITING_APPROVAL);

            assertThat(run.approveAndResume("design")).isEqualTo(RunOutcome.PAUSED);
            assertImplementEscalatedWhileSiblingsRan(run);

            assertThat(run.approveAndResume("implement")).isEqualTo(RunOutcome.PAUSED);
            assertThat(run.orchestrator().state().status("release")).isEqualTo(NodeStatus.AWAITING_APPROVAL);
            assertRealTestFailureWasRolledBackAndRetried(run);
            assertJoinWaitedForAllDependencies(run);

            assertThat(run.approveAndResume("release")).isEqualTo(RunOutcome.COMPLETED);
            assertMetricsAndApprovals(run);
            assertThat(FileTrees.treeHash(run.workspace()))
                    .as("greenfield output is the committed baseline (bless-baseline.sh)")
                    .isEqualTo(FileTrees.treeHash(ScenarioHarness.repoRoot().resolve("shortener-service")));
            assertReport(run.report());
            assertQualityEvidence(run);
            assertGitHistory(run);
        }
    }

    /** The QA, review and requirements evidence the roles must produce, as measured by the gates. */
    private static void assertQualityEvidence(ScenarioHarness run) throws Exception {
        assertThat(gateDetail(run, "qa_report", "test-coverage"))
                .contains("branch 100.0%", "target 100%")
                .contains("below target (documented): com.example.shortener.ShortenerApplication",
                        "com.example.shortener.service.Sha256");
        assertThat(gateDetail(run, "qa_report", "functional-coverage")).startsWith("9 of 9 acceptance criteria mapped to");
        assertThat(run.events()).anyMatch(event -> event.type() == EventType.GATE_PASSED
                && event.nodeId().equals("review") && event.payload(Payload.GatePassed.class).gate().equals("review-complete"));
        String report = run.report();
        assertThat(report).contains("## Quality evidence", "### User stories (`requirements`)", "| `docs/design.md` | 2 |",
                "### Functional coverage (`qa_report`)", "### Code review (`review`): GO", "Reviewed 46 of 46 submitted files.",
                "| FIXED |", "| DEFERRED |", "### Workspace git history", "| demo-reviewer |");
    }

    /** One commit per step that promoted files, authored by the agent role, approvals recorded as trailers. */
    private static void assertGitHistory(ScenarioHarness run) throws Exception {
        List<String> committedNodes = run.events().stream().filter(event -> event.type() == EventType.NODE_DONE)
                .filter(event -> event.payload(Payload.NodeDone.class).commit() != null).map(Event::nodeId).toList();
        assertThat(committedNodes).containsExactlyInAnyOrder("design", "implement", "unit_tests", "integration_tests", "docs", "release");
        String log = git(run, "log", "--format=%an|%s%n%b");
        assertThat(git(run, "rev-list", "--count", "HEAD").strip()).isEqualTo("6");
        assertThat(log).contains("developer|implement:", "Approved-by: demo-reviewer", "Agent: tester");
        assertThat(git(run, "status", "--porcelain").strip()).as("every promoted file is committed").isEmpty();
    }

    private static String gateDetail(ScenarioHarness run, String node, String gate) {
        return run.events().stream().filter(event -> event.type() == EventType.GATE_PASSED && event.nodeId().equals(node))
                .map(event -> event.payload(Payload.GatePassed.class))
                .filter(passed -> passed.gate().equals(gate)).map(Payload.GatePassed::detail).findFirst().orElseThrow();
    }

    private static String git(ScenarioHarness run, String... arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(run.workspace().toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor()).as("git %s", String.join(" ", arguments)).isZero();
        return output;
    }

    private static void assertImplementEscalatedWhileSiblingsRan(ScenarioHarness run) {
        assertThat(run.orchestrator().state().status("implement")).isEqualTo(NodeStatus.AWAITING_APPROVAL);
        assertThat(run.events("implement", EventType.APPROVAL_REQUESTED).getFirst().payload(Payload.ApprovalRequested.class).reasons())
                .anyMatch(reason -> reason.startsWith("PomChangeRule"));
        assertThat(run.orchestrator().state().status("docs")).as("docs ran in parallel with implement").isEqualTo(NodeStatus.DONE);
        assertThat(run.orchestrator().state().status("security_review")).isEqualTo(NodeStatus.DONE);
    }

    private static void assertRealTestFailureWasRolledBackAndRetried(ScenarioHarness run) {
        List<Event> unitFailures = run.events("unit_tests", EventType.GATE_FAILED);
        assertThat(unitFailures).hasSize(1);
        assertThat(unitFailures.getFirst().payload(Payload.GateFailed.class).gate()).isEqualTo("unit-tests");
        assertThat(unitFailures.getFirst().payload(Payload.GateFailed.class).reason()).contains("Expected size: 8 but was: 7");
        assertThat(run.events("unit_tests", EventType.ATTEMPT_DISCARDED)).hasSize(1);
        assertThat(run.events("unit_tests", EventType.AGENT_CALLED)).extracting(e -> e.payload(Payload.AgentCalled.class).attempt()).containsExactly(1, 2);
    }

    private static void assertJoinWaitedForAllDependencies(ScenarioHarness run) {
        long reviewStarted = run.seq("review", EventType.NODE_STARTED);
        for (String dependency : List.of("qa_report", "docs", "security_review")) {
            assertThat(reviewStarted).as("join waits for %s", dependency).isGreaterThan(run.seq(dependency, EventType.NODE_DONE));
        }
        long qaStarted = run.seq("qa_report", EventType.NODE_STARTED);
        for (String dependency : List.of("unit_tests", "integration_tests")) {
            assertThat(qaStarted).as("the QA report waits for %s", dependency).isGreaterThan(run.seq(dependency, EventType.NODE_DONE));
        }
    }

    private static void assertMetricsAndApprovals(ScenarioHarness run) {
        RunMetrics metrics = run.metrics();
        assertThat(metrics.retries()).isEqualTo(1);
        assertThat(metrics.rollbacks()).isEqualTo(1);
        assertThat(metrics.approvalsGranted()).isEqualTo(3);
        assertThat(metrics.firstPassNodes()).isEqualTo(9);
        assertThat(metrics.mttr()).isPresent();
        assertThat(run.events().stream().filter(e -> e.type() == EventType.APPROVED).map(Event::nodeId))
                .containsExactly("design", "implement", "release");
    }

    private static void assertReport(String report) {
        assertThat(report).contains("## Run summary", "```mermaid", "## Timeline", "## Metrics", "## Approvals",
                "## Decision lineage", "## Policy and gate results", "## Invalidation and replan history");
        String approvals = report.substring(report.indexOf("## Approvals"), report.indexOf("## Decision lineage"));
        assertThat(approvals.lines().filter(line -> line.contains("| APPROVED | demo-reviewer |"))).hasSize(3);
    }
}
