package com.example.agentic.scenarios;

import com.example.agentic.core.engine.RunOutcome;
import com.example.agentic.core.metrics.RunMetrics;
import com.example.agentic.core.state.Artifact;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Section 9.2: real impact analysis, a graph patch inserting db_migration, a migration approval, and a rolled-back regression. */
@Execution(ExecutionMode.CONCURRENT)
class BrownfieldScenarioTest {

    @TempDir
    Path runs;

    @Test
    void addsLinkExpiryWithoutBreakingV1() throws Exception {
        try (ScenarioHarness run = new ScenarioHarness("brownfield", runs)) {
            assertThat(run.start()).isEqualTo(RunOutcome.PAUSED);

            Artifact analysis = run.orchestrator().artifacts()
                    .get(run.orchestrator().state().node("analysis").currentHash()).orElseThrow();
            Map<?, ?> scan = (Map<?, ?>) analysis.data().get("scan");
            assertThat((List<?>) scan.get("routes")).extracting(route -> ((Map<?, ?>) route).get("method") + " " + ((Map<?, ?>) route).get("path"))
                    .contains("POST /api/v1/links", "GET /api/v1/links/{code}/stats", "GET /{code:[A-Za-z0-9_-]{3,32}}");
            List<String> impacted = ((List<?>) analysis.data().get("impact")).stream()
                    .map(entry -> String.valueOf(((Map<?, ?>) entry).get("file"))).toList();
            assertThat(impacted)
                    .anyMatch(file -> file.endsWith("ShortLink.java"))
                    .anyMatch(file -> file.endsWith("JdbcLinkRepository.java"))
                    .anyMatch(file -> file.endsWith("LinkController.java"))
                    .contains("openapi.yaml");

            Event replan = run.events("analysis", EventType.REPLAN).getFirst();
            assertThat(replan.payload(Payload.Replan.class).kind()).isEqualTo(Payload.Replan.GRAPH_PATCH);
            assertThat(replan.payload(Payload.Replan.class).addedNodes()).containsExactly("db_migration");
            assertThat(run.orchestrator().state().graph().require("implement").dependsOn()).containsExactly("db_migration");
            assertThat(run.orchestrator().state().status("db_migration")).isEqualTo(NodeStatus.AWAITING_APPROVAL);
            assertThat(run.events("db_migration", EventType.APPROVAL_REQUESTED).getFirst().payload(Payload.ApprovalRequested.class).reasons())
                    .anyMatch(reason -> reason.startsWith("MigrationPathRule"));

            assertThat(run.approveAndResume("db_migration")).isEqualTo(RunOutcome.PAUSED);

            List<Event> implementFailures = run.events("implement", EventType.GATE_FAILED);
            assertThat(implementFailures).hasSize(1);
            assertThat(implementFailures.getFirst().payload(Payload.GateFailed.class).gate()).isEqualTo("regression-tests");
            assertThat(implementFailures.getFirst().payload(Payload.GateFailed.class).reason()).contains("expected:<302> but was:<410>");
            assertThat(run.events("implement", EventType.ATTEMPT_DISCARDED)).hasSize(1);
            assertThat(run.orchestrator().state().status("implement")).isEqualTo(NodeStatus.DONE);

            assertThat(run.approveAndResume("release")).isEqualTo(RunOutcome.COMPLETED);

            RunMetrics metrics = run.metrics();
            assertThat(metrics.retries()).isEqualTo(1);
            assertThat(metrics.replans()).isEqualTo(1);
            assertThat(metrics.approvalsGranted()).isEqualTo(2);
            assertThat(metrics.totalNodes()).isEqualTo(9);
            assertThat(Files.readString(run.workspace().resolve("src/main/resources/db/migration/V2__add_expiry.sql")))
                    .contains("expires_at");
            assertThat(Files.readString(run.workspace().resolve("src/main/java/com/example/shortener/service/ShortenerService.java")))
                    .contains("link.isExpiredAt(clock.instant())").doesNotContain("link.expiresAt() == null ||");
            assertThat(run.report()).contains("### Before re-plan").contains("db_migration");
        }
    }
}
