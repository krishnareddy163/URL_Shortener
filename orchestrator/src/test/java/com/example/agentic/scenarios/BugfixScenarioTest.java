package com.example.agentic.scenarios;

import com.example.agentic.core.engine.RunOutcome;
import com.example.agentic.core.metrics.RunMetrics;
import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.Payload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Brownfield bug fix of a real v1 defect (trailing-dot host names bypass the SSRF block): a behavior-preserving
 * refactor, a reproduction test proven to fail before the fix, an incomplete fix rolled back, and a governed release.
 */
@Execution(ExecutionMode.CONCURRENT)
class BugfixScenarioTest {
    private static final String CLASSIFIER = "src/main/java/com/example/shortener/service/HostClassifier.java";

    @TempDir
    Path runs;

    @Test
    void fixesTheTrailingDotBypassTestFirst() throws Exception {
        try (ScenarioHarness run = new ScenarioHarness("bugfix", runs)) {
            assertThat(run.start()).isEqualTo(RunOutcome.PAUSED);

            assertThat(run.events("refactor", EventType.GATE_PASSED))
                    .extracting(event -> event.payload(Payload.GatePassed.class).gate()).contains("regression-tests");
            assertThat(run.events("reproduce", EventType.GATE_PASSED))
                    .extracting(event -> event.payload(Payload.GatePassed.class).gate()).contains("reproduces-defect");
            assertThat(run.seq("reproduce", EventType.NODE_DONE)).isLessThan(run.seq("fix", EventType.NODE_STARTED));

            List<Event> fixFailures = run.events("fix", EventType.GATE_FAILED);
            assertThat(fixFailures).hasSize(1);
            Payload.GateFailed failure = fixFailures.getFirst().payload(Payload.GateFailed.class);
            assertThat(failure.gate()).isEqualTo("regression-tests");
            assertThat(failure.reason()).contains("http://printer.local./ must be rejected")
                    .doesNotContain("http://localhost./admin must be rejected");
            assertThat(run.events("fix", EventType.ATTEMPT_DISCARDED)).hasSize(1);

            assertThat(run.approveAndResume("fix")).isEqualTo(RunOutcome.PAUSED);
            assertThat(run.approveAndResume("release")).isEqualTo(RunOutcome.COMPLETED);

            RunMetrics metrics = run.metrics();
            assertThat(metrics.totalNodes()).isEqualTo(7);
            assertThat(metrics.retries()).isEqualTo(1);
            assertThat(metrics.approvalsGranted()).isEqualTo(2);
            assertThat(Files.readString(run.workspace().resolve(CLASSIFIER))).contains("absolute DNS name");
            assertThat(run.workspace().resolve("src/test/java/com/example/shortener/service/TrailingDotHostTest.java")).exists();
            assertThat(run.report()).contains("reproduces-defect").contains("TrailingDotHostTest");
        }
    }
}
