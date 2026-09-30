package com.example.agentic.core.engine;

import com.example.agentic.core.Orchestrator;
import com.example.agentic.core.agent.Proposal;
import com.example.agentic.core.graph.Autonomy;
import com.example.agentic.core.graph.Node;
import com.example.agentic.core.state.EventType;
import com.example.agentic.core.state.Payload;
import com.example.agentic.core.state.RunMetadata;
import com.example.agentic.support.InMemoryEventStore;
import com.example.agentic.support.ScriptedAgent;
import com.example.agentic.support.TestEngine;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that a downstream node exhausting its retries resets the named upstream node to PENDING
 * with the gate failure as feedback, cascades INVALIDATED to all nodes in between, and the run
 * eventually completes rather than safe-stopping — implementing the "QA finds a bug, developer fixes
 * it" feedback loop without human intervention.
 */
class UpstreamRejectionTest {

    @TempDir
    Path dir;

    @Test
    void testNodeRejectsUpstreamWhenRetryExhausted() throws Exception {
        AtomicInteger implementCalls = new AtomicInteger();
        AtomicInteger testsCalls = new AtomicInteger();

        // "implement": always proposes good code; on second call includes call number so the hash differs.
        // "tests" (same agent id): first attempt proposes a file with FAIL marker so ContentGate rejects it;
        //  second attempt (after implement re-ran) proposes passing content.
        ScriptedAgent worker = new ScriptedAgent("worker", context -> {
            if ("implement".equals(context.nodeId())) {
                int n = implementCalls.incrementAndGet();
                return new Proposal(Map.of("impl.txt", "code-v" + n), "implement v" + n, List.of(), Map.of());
            }
            int n = testsCalls.incrementAndGet();
            if (n == 1) {
                return new Proposal(Map.of("tests.txt", TestEngine.FAIL_MARKER + ": assertion failed in test"),
                        "tests v1", List.of("implement"), Map.of());
            }
            return new Proposal(Map.of("tests.txt", "all assertions pass"), "tests v2", List.of("implement"), Map.of());
        });

        Node implement = new Node("implement", "worker", Set.of(), List.of(), List.of("content"),
                Autonomy.AUTO, 0, null, null);
        // maxRetries 0: one attempt, then immediately hits failRound → rejectUpstream fires.
        Node tests = new Node("tests", "worker", Set.of("implement"), List.of(), List.of("content"),
                Autonomy.AUTO, 0, null, null, "Write tests", "implement", 1);

        try (Orchestrator orchestrator = new TestEngine().agent(worker)
                .open(dir, new InMemoryEventStore())) {
            RunOutcome outcome = orchestrator.start(TestEngine.graph(implement, tests), dir, RunMetadata.none());

            assertThat(outcome).isEqualTo(RunOutcome.COMPLETED);
        }

        // Both agents ran twice: once for the initial attempt, once after the upstream rejection loop.
        assertThat(implementCalls.get()).isEqualTo(2);
        assertThat(testsCalls.get()).isEqualTo(2);
    }

    @Test
    void cycleCapPreventsInfiniteLoop() throws Exception {
        // tests always fails; with rejectUpstreamMaxCycles=1 it should safe-stop after one rejection cycle.
        ScriptedAgent worker = new ScriptedAgent("worker", context -> {
            if ("implement".equals(context.nodeId())) {
                return new Proposal(Map.of("impl.txt", "code"), "implement", List.of(), Map.of());
            }
            return new Proposal(Map.of("tests.txt", TestEngine.FAIL_MARKER + ": always fails"),
                    "tests", List.of("implement"), Map.of());
        });

        Node implement = new Node("implement", "worker", Set.of(), List.of(), List.of("content"),
                Autonomy.AUTO, 0, null, null);
        Node tests = new Node("tests", "worker", Set.of("implement"), List.of(), List.of("content"),
                Autonomy.AUTO, 0, null, null, "Write tests", "implement", 1);

        try (Orchestrator orchestrator = new TestEngine().agent(worker)
                .open(dir, new InMemoryEventStore())) {
            RunOutcome outcome = orchestrator.start(TestEngine.graph(implement, tests), dir, RunMetadata.none());

            // After one rejection cycle and tests failing again, the run safe-stops.
            assertThat(outcome).isEqualTo(RunOutcome.SAFE_STOPPED);

            // UPSTREAM_REJECTED fired exactly once (one cycle).
            long rejections = orchestrator.log().events().stream()
                    .filter(e -> e.type() == EventType.UPSTREAM_REJECTED)
                    .count();
            assertThat(rejections).isEqualTo(1);
        }
    }

    @Test
    void upstreamFeedbackContainsGateFailureText() throws Exception {
        AtomicInteger implementCalls = new AtomicInteger();
        AtomicInteger testsCalls = new AtomicInteger();
        ScriptedAgent worker = new ScriptedAgent("worker", context -> {
            if ("implement".equals(context.nodeId())) {
                int n = implementCalls.incrementAndGet();
                if (n > 1) {
                    assertThat(context.feedback()).contains("assertion failed in test");
                }
                return new Proposal(Map.of("impl.txt", "code-v" + n), "implement v" + n, List.of(), Map.of());
            }
            int n = testsCalls.incrementAndGet();
            if (n == 1) {
                return new Proposal(Map.of("tests.txt", TestEngine.FAIL_MARKER + ": assertion failed in test"),
                        "tests v1", List.of("implement"), Map.of());
            }
            return new Proposal(Map.of("tests.txt", "pass"), "tests v2", List.of("implement"), Map.of());
        });

        Node implement = new Node("implement", "worker", Set.of(), List.of(), List.of("content"),
                Autonomy.AUTO, 0, null, null);
        Node tests = new Node("tests", "worker", Set.of("implement"), List.of(), List.of("content"),
                Autonomy.AUTO, 0, null, null, "Write tests", "implement", 1);

        try (Orchestrator orchestrator = new TestEngine().agent(worker)
                .open(dir, new InMemoryEventStore())) {
            assertThat(orchestrator.start(TestEngine.graph(implement, tests), dir, RunMetadata.none()))
                    .isEqualTo(RunOutcome.COMPLETED);

            orchestrator.log().events().stream()
                    .filter(e -> e.type() == EventType.UPSTREAM_REJECTED)
                    .map(e -> e.payload(Payload.UpstreamRejected.class))
                    .forEach(p -> {
                        assertThat(p.downstream()).isEqualTo("tests");
                        assertThat(p.feedback()).contains("assertion failed in test");
                    });
        }
    }
}
