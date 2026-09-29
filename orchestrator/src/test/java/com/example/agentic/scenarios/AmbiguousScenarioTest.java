package com.example.agentic.scenarios;

import com.example.agentic.core.engine.RunOutcome;
import com.example.agentic.core.state.Event;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.NodeStatus;
import com.example.agentic.core.state.Payload;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Section 9.3: blocking clarification, variant execution, then an answer change that invalidates the whole chain. */
@Execution(ExecutionMode.CONCURRENT)
class AmbiguousScenarioTest {
    private static final Path VALIDATOR = Path.of("src/main/java/com/example/shortener/service/UrlValidator.java");

    @TempDir
    Path runs;

    @Test
    void clarifiesThenReplansWhenTheAnswerChanges() throws Exception {
        try (ScenarioHarness run = new ScenarioHarness("ambiguous", runs)) {
            assertThat(run.start()).isEqualTo(RunOutcome.PAUSED);
            assertThat(run.orchestrator().state().status("requirements")).isEqualTo(NodeStatus.AWAITING_CLARIFICATION);
            assertThat(run.events("requirements", EventType.CLARIFICATION_REQUESTED)).extracting(e -> e.payload(Payload.ClarificationRequested.class).questionId())
                    .containsExactly("q-secure");

            run.orchestrator().clarifications().answer("q-secure", "https-only", ScenarioHarness.APPROVER);
            assertThat(run.resume()).isEqualTo(RunOutcome.PAUSED);
            assertThat(run.approveAndResume("release")).isEqualTo(RunOutcome.COMPLETED);
            assertThat(Files.readString(run.workspace().resolve(VALIDATOR))).contains("URL scheme must be https");
            String httpsOnlyRelease = run.orchestrator().state().node("release").approvedHash();

            run.orchestrator().clarifications().answer("q-secure", "domain-blocklist", ScenarioHarness.APPROVER);

            List<Event> invalidated = run.events().stream().filter(e -> e.type() == EventType.INVALIDATED).toList();
            assertThat(invalidated).extracting(Event::nodeId)
                    .containsExactly("design", "implement", "tests", "docs", "review", "release");
            assertThat(invalidated.getLast().payload(Payload.Invalidated.class).approvalRevoked()).isTrue();
            assertThat(httpsOnlyRelease).isNotNull();
            assertThat(run.orchestrator().state().node("release").approvedHash()).isNull();
            assertThat(Files.readString(run.workspace().resolve(VALIDATOR))).as("https-only change reverted")
                    .doesNotContain("must be https");

            assertThat(run.resume()).isEqualTo(RunOutcome.PAUSED);
            assertThat(run.events("implement", EventType.NODE_STARTED).getLast().payload(Payload.NodeStarted.class).variant()).isEqualTo("domain-blocklist");
            assertThat(run.approveAndResume("release")).isEqualTo(RunOutcome.COMPLETED);

            assertThat(run.workspace().resolve("src/main/java/com/example/shortener/service/DomainBlocklist.java")).exists();
            assertThat(Files.readString(run.workspace().resolve(VALIDATOR))).contains("blocklisted").doesNotContain("must be https");
            assertThat(run.metrics().invalidations()).isEqualTo(6);
            assertThat(run.metrics().approvalsGranted()).isEqualTo(2);

            String report = run.report();
            assertThat(report).contains("### Before re-plan", "### After (final)", "q-analytics: click counts and daily buckets",
                    "cascade [design, implement, tests, docs, review, release]");
        }
    }
}
